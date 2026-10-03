package io.luma.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.luma.reader.data.LibraryRepository
import io.luma.reader.epub.EpubParser
import io.luma.reader.model.Book
import io.luma.reader.model.AppSettings
import io.luma.reader.model.LibrarySnapshot
import io.luma.reader.model.LibrarySort
import io.luma.reader.model.ReaderSettings
import io.luma.reader.model.ReaderAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppUiState(
    val library: LibrarySnapshot = LibrarySnapshot(),
    val activeBookId: String? = null,
    val importing: Boolean = false,
    val importCount: Int = 0,
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application)
    private val parser = EpubParser(application)
    private val _state = MutableStateFlow(AppUiState(library = repository.load()))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        refreshMissingCovers()
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.importing) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true, importCount = uris.size, message = null) }
            val imported = mutableListOf<Book>()
            val failures = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching { parser.import(uri) }
                        .onSuccess(imported::add)
                        .onFailure { failures += (it.message ?: "Archivo EPUB no compatible") }
                }
            }
            mutateLibrary { current ->
                current.copy(books = imported + current.books)
            }
            val message = when {
                failures.isEmpty() -> "${imported.size} ${if (imported.size == 1) "libro importado" else "libros importados"}"
                imported.isEmpty() -> "No se pudo importar: ${failures.first()}"
                else -> "${imported.size} importados; ${failures.size} no compatibles"
            }
            _state.update { it.copy(importing = false, importCount = 0, message = message) }
        }
    }

    fun openBook(bookId: String) {
        mutateBook(bookId) { it.copy(lastOpenedAt = System.currentTimeMillis()) }
        _state.update { it.copy(activeBookId = bookId) }
    }

    fun closeBook() {
        _state.update { it.copy(activeBookId = null) }
    }

    fun updateReadingPosition(bookId: String, chapter: Int, chapterProgress: Float) {
        val book = _state.value.library.books.firstOrNull { it.id == bookId } ?: return
        val safeChapter = chapter.coerceIn(0, book.chapters.lastIndex)
        val safeChapterProgress = chapterProgress.coerceIn(0f, 1f)
        val overall = ((safeChapter + safeChapterProgress) / book.chapters.size).coerceIn(0f, 1f)
        mutateBook(bookId) {
            it.copy(
                chapterIndex = safeChapter,
                chapterProgress = safeChapterProgress,
                progress = overall,
                lastOpenedAt = System.currentTimeMillis()
            )
        }
    }

    fun toggleFavorite(bookId: String) =
        mutateBook(bookId) { it.copy(favorite = !it.favorite) }

    fun moveToCollection(bookId: String, collectionId: String?) =
        mutateBook(bookId) { it.copy(collectionId = collectionId) }

    fun addCollection(name: String, colorIndex: Int) {
        if (name.isBlank()) return
        mutateLibrary {
            it.copy(collections = it.collections + repository.createCollection(name, colorIndex))
        }
    }

    fun deleteCollection(collectionId: String) {
        mutateLibrary {
            it.copy(
                collections = it.collections.filterNot { collection -> collection.id == collectionId },
                books = it.books.map { book ->
                    if (book.collectionId == collectionId) book.copy(collectionId = null) else book
                }
            )
        }
    }

    fun deleteBook(bookId: String) {
        val book = _state.value.library.books.firstOrNull { it.id == bookId } ?: return
        viewModelScope.launch(Dispatchers.IO) { repository.deleteBook(book) }
        mutateLibrary { it.copy(books = it.books.filterNot { item -> item.id == bookId }) }
        if (_state.value.activeBookId == bookId) closeBook()
    }

    fun updateBookCover(bookId: String, uri: Uri) {
        val book = _state.value.library.books.firstOrNull { it.id == bookId } ?: return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { repository.copyCustomCover(book, uri) }
            }
            result.onSuccess { path ->
                mutateBook(bookId) { it.copy(coverPath = path) }
                _state.update { it.copy(message = "Portada actualizada") }
            }.onFailure { error ->
                _state.update {
                    it.copy(message = error.message ?: "No se pudo cambiar la portada")
                }
            }
        }
    }

    fun updateBookDetails(bookId: String, title: String, review: String, rating: Int) {
        if (title.isBlank()) return
        mutateBook(bookId) {
            it.copy(
                title = title.trim(),
                review = review.trim(),
                rating = rating.coerceIn(0, 5)
            )
        }
    }

    fun saveAnnotation(bookId: String, annotation: ReaderAnnotation) =
        mutateBook(bookId) { book ->
            book.copy(
                annotations = book.annotations.filterNot { it.id == annotation.id } + annotation
            )
        }

    fun deleteAnnotation(bookId: String, annotationId: String) =
        mutateBook(bookId) { book ->
            book.copy(annotations = book.annotations.filterNot { it.id == annotationId })
        }

    fun addReadingTime(bookId: String, elapsedMillis: Long) {
        if (elapsedMillis < 1_000L) return
        mutateBook(bookId) { book ->
            book.copy(readingTimeMillis = (book.readingTimeMillis + elapsedMillis).coerceAtLeast(0L))
        }
    }

    fun updateSettings(settings: ReaderSettings) =
        mutateLibrary { it.copy(settings = settings) }

    fun updateAppSettings(settings: AppSettings) =
        mutateLibrary { it.copy(appSettings = settings) }

    fun updateBookSettings(bookId: String, settings: ReaderSettings?) =
        mutateBook(bookId) { it.copy(readerSettings = settings) }

    fun setSort(sort: LibrarySort) =
        mutateLibrary { it.copy(sort = sort) }

    fun setGridView(grid: Boolean) =
        mutateLibrary { it.copy(gridView = grid) }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun refreshMissingCovers() {
        viewModelScope.launch(Dispatchers.IO) {
            val detected = _state.value.library.books
                .filter { book ->
                    book.coverPath?.let { path -> File(path).exists() } != true
                }
                .mapNotNull { book -> parser.detectCover(book)?.let { book.id to it } }
                .toMap()
            if (detected.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    mutateLibrary { library ->
                        library.copy(
                            books = library.books.map { book ->
                                detected[book.id]?.let { book.copy(coverPath = it) } ?: book
                            }
                        )
                    }
                }
            }
        }
    }

    private fun mutateBook(bookId: String, transform: (Book) -> Book) {
        mutateLibrary { library ->
            library.copy(books = library.books.map { if (it.id == bookId) transform(it) else it })
        }
    }

    private fun mutateLibrary(transform: (LibrarySnapshot) -> LibrarySnapshot) {
        val updated = transform(_state.value.library)
        _state.update { it.copy(library = updated) }
        repository.save(updated)
    }
}
