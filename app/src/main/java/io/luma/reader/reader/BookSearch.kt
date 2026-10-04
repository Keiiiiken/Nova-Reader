package io.luma.reader.reader

import io.luma.reader.epub.EpubParser
import io.luma.reader.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class BookSearchResult(val chapterIndex: Int, val title: String, val excerpt: String, val matches: Int)

internal object BookSearch {
    suspend fun search(book: Book, query: String): List<BookSearchResult> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.length < 2) return@withContext emptyList()
        buildList {
            book.chapters.forEachIndexed { index, chapter ->
                currentCoroutineContext().ensureActive()
                val text = EpubParser.htmlToText(ChapterContent.read(book, index))
                val first = text.indexOf(term, ignoreCase = true)
                if (first >= 0) {
                    var count = 0
                    var offset = first
                    while (offset >= 0) {
                        count++
                        offset = text.indexOf(term, offset + term.length, ignoreCase = true)
                    }
                    add(BookSearchResult(index, chapter.title,
                        text.substring((first - 60).coerceAtLeast(0), (first + term.length + 100).coerceAtMost(text.length)).replace(Regex("\\s+"), " "), count))
                }
            }
        }
    }
}
