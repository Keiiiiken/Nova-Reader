package io.luma.reader.drive

import io.luma.reader.data.BackupRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class DriveBackup(val id: String, val name: String, val modifiedTime: String, val size: Long)

/** Uses only drive.appdata. Access tokens remain in memory for one user-initiated operation. */
internal class DriveBackupClient(private val accessToken: String, private val connectionFactory: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) {
    suspend fun list(): List<DriveBackup> = listPage(null).first

    private suspend fun listPage(pageToken: String?): Pair<List<DriveBackup>, String?> {
        val q = "trashed = false and appProperties has { key='novaBackup' and value='1' }"
        val url = "$API/files?spaces=appDataFolder&pageSize=100&orderBy=modifiedTime%20desc&fields=nextPageToken,files(id,name,modifiedTime,size)&q=${encode(q)}" +
            (pageToken?.let { "&pageToken=${encode(it)}" } ?: "")
        return connection(url).useConnection { request ->
            requireSuccess(request)
            val response = JSONObject(request.inputStream.bufferedReader().use { it.readText() })
            val files = response.getJSONArray("files")
            (0 until files.length()).map { index -> files.getJSONObject(index).let {
                DriveBackup(it.getString("id"), it.getString("name"), it.getString("modifiedTime"), it.optLong("size"))
            } } to response.optString("nextPageToken").takeIf { it.isNotBlank() }
        }
    }

    /** Update the latest file; remove legacy duplicates only after the upload completes. */
    suspend fun saveLatest(file: File, onProgress: (Float) -> Unit) {
        val previous = mutableListOf<DriveBackup>()
        var pageToken: String? = null
        do {
            currentCoroutineContext().ensureActive()
            val (files, next) = listPage(pageToken)
            previous += files
            pageToken = next
        } while (pageToken != null)
        val latest = previous.firstOrNull()
        upload(file, latest?.id, onProgress)
        previous.drop(1).forEach { old ->
            currentCoroutineContext().ensureActive()
            try {
                connection("$API/files/${encode(old.id)}", "DELETE").useConnection { request ->
                    if (request.responseCode != 404) requireSuccess(request)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                throw IllegalStateException("La copia nueva está guardada, pero no se pudieron borrar todas las copias antiguas. Vuelve a guardar para terminar la limpieza.", error)
            }
        }
    }

    suspend fun upload(file: File, replaceId: String? = null, onProgress: (Float) -> Unit) {
        require(file.length() <= BackupRepository.MAX_BACKUP) { "La copia supera 2 GiB" }
        val metadata = JSONObject().put("name", "NovaReader-${java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.US).format(java.util.Date())}.zip")
            .put("appProperties", JSONObject().put("novaBackup", "1").put("formatVersion", "1"))
        if (replaceId == null) metadata.put("parents", JSONArray().put("appDataFolder"))
        val target = "https://www.googleapis.com/upload/drive/v3/files" + (replaceId?.let { "/${encode(it)}" } ?: "")
        val session = connection("$target?uploadType=resumable&fields=id,name", if (replaceId == null) "POST" else "PATCH").useConnection { request ->
            request.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            request.setRequestProperty("X-Upload-Content-Type", "application/zip")
            request.setRequestProperty("X-Upload-Content-Length", file.length().toString())
            request.doOutput = true
            request.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
            requireSuccess(request)
            request.getHeaderField("Location") ?: error("Drive no inició la subida")
        }
        val sessionUrl = URL(session)
        require(sessionUrl.protocol == "https" && sessionUrl.host == "www.googleapis.com") { "Destino de subida no válido" }
        val chunk = ByteArray(8 * 1024 * 1024) // Multiple of Drive's required 256 KiB.
        java.io.RandomAccessFile(file, "r").use { input ->
            var offset = 0L
            while (offset < file.length()) {
                currentCoroutineContext().ensureActive()
                input.seek(offset)
                val length = minOf(chunk.size.toLong(), file.length() - offset).toInt()
                input.readFully(chunk, 0, length)
                var sent = false
                for (attempt in 0..2) {
                    currentCoroutineContext().ensureActive()
                    val result = runCatching {
                        connection(session, "PUT").useConnection { request ->
                            request.setRequestProperty("Content-Type", "application/zip")
                            request.setRequestProperty("Content-Range", "bytes $offset-${offset + length - 1}/${file.length()}")
                            request.setFixedLengthStreamingMode(length)
                            request.doOutput = true
                            request.outputStream.use { it.write(chunk, 0, length) }
                            val code = request.responseCode
                            if (code == 308) {
                                val accepted = request.getHeaderField("Range")?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: 0L
                                require(accepted > offset && accepted <= offset + length) { "Drive no confirmó la subida" }
                                accepted
                            } else {
                                requireSuccess(request)
                                require(offset + length == file.length()) { "Drive cerró la subida antes de terminar" }
                                file.length()
                            }
                        }
                    }
                    if (result.isSuccess) {
                        offset = result.getOrThrow(); sent = true; break
                    }
                    if (attempt == 2) throw result.exceptionOrNull()!!
                    delay(1_000L * (attempt + 1))
                }
                check(sent)
                onProgress((offset.toDouble() / file.length()).toFloat())
            }
        }
    }

    suspend fun download(backup: DriveBackup, destination: File, onProgress: (Float) -> Unit) {
        require(backup.size <= BackupRepository.MAX_BACKUP) { "La copia supera 2 GiB" }
        try {
            connection("$API/files/${encode(backup.id)}?alt=media").useConnection { request ->
                requireSuccess(request)
                request.inputStream.use { input -> destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= BackupRepository.MAX_BACKUP) { "La copia supera 2 GiB" }
                        output.write(buffer, 0, count)
                        if (backup.size > 0) onProgress((total.toDouble() / backup.size).toFloat().coerceIn(0f, 1f))
                    }
                    require(backup.size <= 0 || total == backup.size) { "La copia se descargó incompleta. Inténtalo de nuevo." }
                } }
            }
        } catch (error: Throwable) { destination.delete(); throw error }
    }

    private fun connection(url: String, method: String = "GET") = connectionFactory(URL(url)).apply {
        // HttpURLConnection's base transport rejects PATCH. Google APIs support
        // the same POST override used by google-api-java-client MethodOverride.
        requestMethod = if (method == "PATCH") "POST" else method
        if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
        connectTimeout = 30_000; readTimeout = 120_000
        instanceFollowRedirects = false
        setRequestProperty("Authorization", "Bearer $accessToken")
    }
    private fun requireSuccess(request: HttpsURLConnection) {
        when (val code = request.responseCode) {
            in 200..299 -> Unit
            401 -> error("La autorización de Google caducó. Vuelve a conectar Drive.")
            403 -> error("Drive no permite esta operación. Comprueba el permiso de la cuenta y la configuración de la app.")
            404 -> error("La copia ya no está disponible en Drive.")
            429 -> error("Drive está ocupado. Inténtalo de nuevo más tarde.")
            else -> error("No se pudo completar la operación en Drive (HTTP $code).")
        }
    }
    private inline fun <T> HttpsURLConnection.useConnection(block: (HttpsURLConnection) -> T): T = try { block(this) } finally { disconnect() }
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val API = "https://www.googleapis.com/drive/v3"
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}
