package io.luma.reader.reader

import android.net.Uri
import io.luma.reader.epub.EpubParser
import io.luma.reader.model.Book
import org.jsoup.Jsoup
import java.io.File

internal data class InternalLink(val title: String, val text: String, val chapterIndex: Int, val anchorText: String)
internal object InternalLinks {
    fun resolve(book: Book, uri: Uri): InternalLink {
        require(uri.scheme == "file")
        val root = File(book.contentPath).canonicalFile
        val file = File(requireNotNull(uri.path)).canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file.isFile && file.length() <= 4L * 1024 * 1024)
        val document = Jsoup.parse(EpubParser.readDocument(file))
        val id = uri.fragment
        val element = if (id.isNullOrEmpty()) document.body() else document.getElementById(id) ?: error("Referencia inexistente")
        val text = element.text().trim()
        require(text.isNotEmpty())
        val chapter = book.chapters.indexOfFirst { File(root, it.href).canonicalFile == file }
        val anchor = (element.selectFirst("p, li, h1, h2, h3") ?: element).text().take(80)
        return InternalLink(if (id.isNullOrBlank()) "Enlace del libro" else "Referencia del libro", text.take(20_000), chapter, anchor)
    }
}
