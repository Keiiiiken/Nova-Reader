package io.luma.reader.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.luma.reader.epub.EpubParser
import io.luma.reader.model.LibrarySnapshot
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal class BackupRepository(private val context: Context) {
    private val codec = LibraryRepository(context)
    fun create(snapshot: LibrarySnapshot): File {
        val file = File(context.cacheDir, "nova-backup-${UUID.randomUUID()}.zip")
        try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                val manifest = JSONObject().put("format", FORMAT).put("version", 1)
                    .put("createdAt", System.currentTimeMillis())
                    .put("library", JSONObject(codec.encodeSnapshot(snapshot)))
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                snapshot.books.forEach { book ->
                    require(ID.matches(book.id)) { "Identificador de libro inválido" }
                    val epub = File(book.epubPath)
                    require(epub.isFile) { "No se encuentra el EPUB de ${book.title}" }
                    zip.putNextEntry(ZipEntry("books/${book.id}/book.epub"))
                    epub.inputStream().use { copyLimited(it, zip, MAX_EPUB) }; zip.closeEntry()
                    book.coverPath?.let(::File)?.takeIf { it.isFile }?.let { cover ->
                        zip.putNextEntry(ZipEntry("books/${book.id}/cover.image"))
                        cover.inputStream().use { copyLimited(it, zip, MAX_COVER) }; zip.closeEntry()
                    }
                }
            }
            require(file.length() <= MAX_BACKUP) { "La copia supera 2 GiB" }
            return file
        } catch (error: Throwable) { file.delete(); throw error }
    }

    fun copyFrom(uri: Uri): File {
        val file = File(context.cacheDir, "restore-${UUID.randomUUID()}.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { copyLimited(input, it, MAX_BACKUP) }
            } ?: error("No se pudo abrir la copia")
            return file
        } catch (error: Throwable) { file.delete(); throw error }
    }

    /** Stage every book before committing; failed/corrupt archives never replace the library. */
    fun restore(file: File, current: LibrarySnapshot): LibrarySnapshot {
        var remainingExtraction = 2L * 1024 * 1024 * 1024
        val staged = mutableListOf<io.luma.reader.model.Book>()
        try {
            require(file.length() <= MAX_BACKUP) { "La copia supera 2 GiB" }
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: error("No es una copia de Nova Reader")
                val manifest = JSONObject(zip.getInputStream(entry).use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    copyLimited(input, out, 16L * 1024 * 1024)
                    out.toString("UTF-8")
                })
                require(manifest.optString("format") == FORMAT && manifest.optInt("version") == 1) { "Formato de copia no compatible" }
                val source = codec.decodeSnapshot(manifest.getJSONObject("library").toString(), checkFiles = false)
                require(source.books.size <= 2_000 && source.books.map { it.id }.distinct().size == source.books.size) { "La copia contiene libros duplicados o demasiados libros" }
                val allowed = mutableSetOf("manifest.json")
                source.books.forEach { book ->
                    require(ID.matches(book.id)) { "Identificador de libro inválido" }
                    allowed += "books/${book.id}/book.epub"; allowed += "books/${book.id}/cover.image"
                }
                val seen = mutableSetOf<String>()
                zip.entries().asSequence().forEach { item ->
                    require(item.name in allowed && seen.add(item.name)) { "La copia contiene una entrada no válida" }
                }
                source.books.forEach { original ->
                    val epubEntry = zip.getEntry("books/${original.id}/book.epub") ?: error("Falta el EPUB de ${original.title}")
                    val temp = File(context.cacheDir, "restore-book-${UUID.randomUUID()}.epub")
                    val imported = try {
                        zip.getInputStream(epubEntry).use { input -> temp.outputStream().use { copyLimited(input, it, MAX_EPUB) } }
                        io.luma.reader.importer.BookImporter(context).import(Uri.fromFile(temp), forcedFormat = original.format)
                    } finally { temp.delete() }
                    staged += imported
                    remainingExtraction -= File(imported.contentPath).walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    require(remainingExtraction >= 0) { "La copia descomprimida supera 2 GiB" }
                    val cover = zip.getEntry("books/${original.id}/cover.image")?.let { coverEntry ->
                        val output = File(File(imported.epubPath).parentFile, "backup-cover.image")
                        zip.getInputStream(coverEntry).use { input -> output.outputStream().use { copyLimited(input, it, MAX_COVER) } }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(output.path, bounds)
                        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La copia contiene una portada no válida" }
                        output.path
                    } ?: imported.coverPath
                    staged[staged.lastIndex] = original.copy(epubPath = imported.epubPath, contentPath = imported.contentPath,
                        coverPath = cover, chapters = imported.chapters,
                        chapterIndex = original.chapterIndex.coerceIn(imported.chapters.indices),
                        chapterProgress = original.chapterProgress.coerceIn(0f, 1f), progress = original.progress.coerceIn(0f, 1f),
                        collectionId = original.collectionId?.takeIf { id -> source.collections.any { it.id == id } })
                }
                val restoredIds = staged.map { it.id }.toSet()
                return source.copy(books = staged + current.books.filterNot { it.id in restoredIds },
                    collections = source.collections + current.collections.filterNot { old -> source.collections.any { it.id == old.id } })
            }
        } catch (error: Throwable) {
            staged.forEach { File(it.epubPath).parentFile?.deleteRecursively() }
            throw error
        }
    }

    companion object {
        const val FORMAT = "nova-reader-backup"
        const val MAX_BACKUP = 2L * 1024 * 1024 * 1024
        private const val MAX_EPUB = 512L * 1024 * 1024
        private const val MAX_COVER = 32L * 1024 * 1024
        private val ID = Regex("[A-Za-z0-9_-]{1,128}")
        internal fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
            var total = 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "El archivo supera el tamaño permitido" }
                output.write(buffer, 0, count)
            }
            return total
        }
    }
}
