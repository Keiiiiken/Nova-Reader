package io.luma.reader.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import io.luma.reader.data.BackupRepository
import io.luma.reader.epub.EpubParser
import io.luma.reader.model.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

internal class DuplicateBookException : IllegalArgumentException("Este archivo ya está en tu biblioteca o papelera")

internal class BookImporter(private val context: Context) {
    fun import(uri: Uri, knownHashes: Set<String> = emptySet(), forcedFormat: BookFormat? = null): Book {
        val stage = File(context.cacheDir, "import-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> stage.outputStream().use {
                BackupRepository.copyLimited(input, it, 512L * 1024 * 1024)
            } } ?: error("No se pudo abrir el archivo")
            val hash = fingerprint(stage)
            if (hash in knownHashes) throw DuplicateBookException()
            val format = forcedFormat ?: if (stage.inputStream().use { input -> ByteArray(5).also { input.read(it) }.toString(Charsets.US_ASCII) } == "%PDF-") BookFormat.PDF
            else ZipFile(stage).use { if (it.getEntry("META-INF/container.xml") != null) BookFormat.EPUB else BookFormat.CBZ }
            val book = if (format == BookFormat.EPUB) EpubParser(context).import(Uri.fromFile(stage))
            else importPages(stage, format, displayName(uri).substringBeforeLast('.').ifBlank { "Libro" })
            return book.copy(fingerprint = hash, format = format, modifiedAt = System.currentTimeMillis())
        } finally { stage.delete() }
    }

    private fun displayName(uri: Uri): String = if (uri.scheme == "file") File(uri.path.orEmpty()).name else
        runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "Libro"
        } }.getOrNull() ?: "Libro"

    private fun importPages(source: File, format: BookFormat, title: String): Book {
        val id = UUID.randomUUID().toString()
        val root = File(context.filesDir, "library/$id").apply { mkdirs() }
        try {
            val original = File(root, "book.${format.name.lowercase()}")
            source.copyTo(original)
            val content = File(root, "content").apply { mkdirs() }
            val chapters = if (format == BookFormat.PDF) {
                PdfRenderer(ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    require(pdf.pageCount in 1..20_000) { "El PDF no contiene páginas o es demasiado grande" }
                    pdf.openPage(0).use { page ->
                        val cover = Bitmap.createBitmap(360, (360f * page.height / page.width).toInt().coerceIn(1, 1024), Bitmap.Config.ARGB_8888)
                        try { cover.eraseColor(android.graphics.Color.WHITE); page.render(cover, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            File(root, "cover.png").outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        } finally { cover.recycle() }
                    }
                    (0 until pdf.pageCount).map { Chapter("Página ${it + 1}", it.toString(), "application/pdf", 1) }
                }
            } else ZipFile(original).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(entries.size <= 20_000) { "El CBZ contiene demasiados archivos" }
                val images = entries.filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                    .sortedBy { entry -> Regex("\\d+").replace(entry.name.lowercase()) { it.value.padStart(12, '0') } }
                require(images.isNotEmpty()) { "El CBZ no contiene imágenes compatibles" }
                var total = 0L
                images.mapIndexed { index, entry ->
                    val output = File(content, "page-$index.image")
                    zip.getInputStream(entry).use { input -> output.outputStream().use { total += BackupRepository.copyLimited(input, it, 64L * 1024 * 1024) } }
                    require(total <= 750L * 1024 * 1024) { "El CBZ descomprimido supera 750 MiB" }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(output.path, bounds)
                    require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000) { "Imagen del CBZ no compatible" }
                    Chapter("Página ${index + 1}", output.name, "image/*", 1)
                }
            }
            val cover = if (format == BookFormat.PDF) File(root, "cover.png") else File(content, chapters.first().href)
            return Book(id, title, "Autor desconocido", original.path, content.path, cover.path, chapters, format = format)
        } catch (e: Throwable) { root.deleteRecursively(); throw e }
    }

    companion object {
        fun fingerprint(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
