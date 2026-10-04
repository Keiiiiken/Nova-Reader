package io.luma.reader.reader

import android.util.LruCache
import io.luma.reader.epub.EpubParser
import io.luma.reader.model.Book
import java.io.File

/** Shared bounded cache used by reader preparation and whole-book search on IO. */
internal object ChapterContent {
    private val cache = object : LruCache<String, String>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: String) = value.length * 2
    }
    fun read(book: Book, chapterIndex: Int): String {
        val root = File(book.contentPath).canonicalFile
        val file = File(root, book.chapters[chapterIndex].href).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Ruta de capítulo inválida" }
        require(file.length() <= 16L * 1024 * 1024) { "El capítulo supera 16 MiB" }
        val key = "${file.path}:${file.lastModified()}:${file.length()}"
        return cache.get(key) ?: EpubParser.readDocument(file).also { cache.put(key, it) }
    }
}
