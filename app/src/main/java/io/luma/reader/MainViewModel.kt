package io.luma.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.luma.reader.data.LibraryRepository
import io.luma.reader.data.LibraryWrites
import io.luma.reader.data.BackupRepository
import io.luma.reader.drive.DriveBackup
import io.luma.reader.drive.DriveBackupClient
import kotlinx.coroutines.CancellationException
import io.luma.reader.model.ReadingAnchor
import io.luma.reader.model.ReaderBookmark
import io.luma.reader.model.ReaderTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import io.luma.reader.epub.EpubParser
import io.luma.reader.model.*
import io.luma.reader.importer.BookImporter
import io.luma.reader.importer.DuplicateBookException
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

data class BackupUiState(
    val busy: Boolean = false,
    val restoring: Boolean = false,
    val operation: String = "",
    val progress: Float? = null,
    val error: String? = null,
    val driveBackups: List<DriveBackup> = emptyList(),
    val accountName: String? = null,
    val lastResult: String? = null,
    val lastSuccess: Long = 0L,
    val scheduleHours: Long = 0L,
    val wifiOnly: Boolean = true,
    val destination: String? = null
)

data class AppUiState(
    val library: LibrarySnapshot = LibrarySnapshot(),
    val loading: Boolean = true,
    val loadingError: String? = null,
    val backup: BackupUiState = BackupUiState(),
    val readerTarget: ReaderTarget? = null,
    val activeBookId: String? = null,
    val importing: Boolean = false,
    val importCount: Int = 0,
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application)
    private val parser = EpubParser(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    internal val syncPreview = MutableStateFlow<io.luma.reader.drive.SyncPreview?>(null)
    private var syncToken: String? = null
    private var saveJob: Job? = null
    private val pendingImports = mutableListOf<Uri>()
    init {
        observeBackupJobs()
        observeNarration()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val old = repository.load()
                val upgraded = old.copy(books = old.books.map { book ->
                    val hash = book.fingerprint.ifBlank { BookImporter.fingerprint(File(book.epubPath)) }
                    val chapters = if (book.format == BookFormat.EPUB && book.chapters.all { it.wordCount == 0 }) book.chapters.map { ch ->
                        val file = File(book.contentPath, ch.href)
                        val words = if (file.isFile && file.length() <= 4L * 1024 * 1024)
                            EpubParser.htmlToText(EpubParser.readDocument(file)).split(Regex("\\s+")).count { it.isNotBlank() } else 0
                        ch.copy(wordCount = words)
                    } else book.chapters
                    book.copy(fingerprint = hash, chapters = chapters)
                })
                val narration = application.getSharedPreferences("nova_narration", android.content.Context.MODE_PRIVATE)
                val recovered = upgraded.copy(books = upgraded.books.map { book ->
                    if (book.id == narration.getString("book", null) && narration.getLong("time", 0) > book.lastOpenedAt) {
                        val chapter = narration.getInt("chapter", 0).coerceIn(book.chapters.indices)
                        val fraction = narration.getFloat("progress", 0f).coerceIn(0f, 1f)
                        book.copy(chapterIndex = chapter, chapterProgress = fraction, progress = book.weightedProgress(chapter, fraction),
                            readingAnchor = ReadingAnchor(narration.getInt("block", 0), narration.getInt("offset", 0)), lastOpenedAt = narration.getLong("time", 0))
                    } else book
                })
                if (recovered != old) repository.save(recovered)
                recovered
            } }
                .onSuccess { library ->
                    _state.update { it.copy(library = library, loading = false) }
                    refreshMissingCovers()
                    purgeExpiredTrash()
                    if (pendingImports.isNotEmpty()) {
                        val uris = pendingImports.toList(); pendingImports.clear(); importBooks(uris)
                    }
                }.onFailure { error ->
                    _state.update { it.copy(loadingError = "No se pudo cargar la biblioteca. Comprueba el espacio disponible y vuelve a abrir la app.") }
                }
        }
    }

    private fun observeNarration() {
        viewModelScope.launch {
            var previous: Triple<String?, Int, Int>? = null
            io.luma.reader.audio.NarrationPlayer.state.collect { value ->
                val key = Triple(value.bookId, value.chapter, value.block)
                if (value.bookId != null && key != previous && !_state.value.loading) {
                    previous = key
                    updateReadingPosition(value.bookId, value.chapter, value.progress)
                    updateAnchor(value.bookId, value.chapter, ReadingAnchor(value.block, value.start))
                }
            }
        }
    }

    fun flushPersistence() {
        if (_state.value.loading || _state.value.backup.restoring) return
        saveJob?.cancel()
        val snapshot = _state.value.library
        LibraryWrites.enqueue {
            runCatching { repository.save(snapshot) }.onFailure {
                _state.update { it.copy(message = "No se pudo guardar el progreso. Comprueba el espacio disponible.") }
            }
        }
    }

    override fun onCleared() {
        flushPersistence()
        super.onCleared()
    }


    fun importBooks(uris: List<Uri>) {
        if (_state.value.loading) { pendingImports.addAll(uris); return }
        if (uris.isEmpty() || _state.value.importing || _state.value.backup.busy) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true, importCount = uris.size, message = null) }
            val imported = mutableListOf<Book>()
            val failures = mutableListOf<String>()
            var duplicates = 0
            val hashes = _state.value.library.books.map { it.fingerprint }.filter { it.isNotBlank() }.toMutableSet()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching { BookImporter(getApplication()).import(uri, hashes) }
                        .onSuccess { imported += it; hashes += it.fingerprint }
                        .onFailure { if (it is DuplicateBookException) duplicates++ else failures += (it.message ?: "Archivo no compatible") }
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
            _state.update { it.copy(importing = false, importCount = 0, message = message + if (duplicates > 0) "; $duplicates duplicados omitidos (revisa también la papelera)" else "") }
        }
    }

    fun openBook(bookId: String) {
        if (_state.value.backup.restoring) {
            _state.update { it.copy(message = "Espera a que termine la restauración para abrir tus libros.") }
            return
        }
        mutateBook(bookId) { it.copy(lastOpenedAt = System.currentTimeMillis()) }
        _state.update { it.copy(activeBookId = bookId, readerTarget = null) }
    }

    fun openAt(bookId: String, target: ReaderTarget) {
        openBook(bookId)
        _state.update { it.copy(readerTarget = target) }
    }
    fun closeBook() {
        flushPersistence()
        _state.update { it.copy(activeBookId = null, readerTarget = null) }
    }
    fun updateAnchor(bookId: String, chapter: Int, anchor: ReadingAnchor) {
        if (_state.value.library.books.firstOrNull { it.id == bookId }?.chapterIndex != chapter) return
        mutateBook(bookId, deferred = true) { it.copy(readingAnchor = anchor) }
    }
    fun addBookmark(bookId: String, bookmark: ReaderBookmark) = mutateBook(bookId) {
        it.copy(bookmarks = it.bookmarks + bookmark)
    }
    fun deleteBookmark(bookId: String, id: String) = mutateBook(bookId) {
        it.copy(bookmarks = it.bookmarks.filterNot { bookmark -> bookmark.id == id }, bookmarkTombstones = it.bookmarkTombstones + id)
    }


    fun updateReadingPosition(bookId: String, chapter: Int, chapterProgress: Float) {
        val book = _state.value.library.books.firstOrNull { it.id == bookId } ?: return
        val safeChapter = chapter.coerceIn(0, book.chapters.lastIndex)
        val safeChapterProgress = chapterProgress.coerceIn(0f, 1f)
        val overall = book.weightedProgress(safeChapter, safeChapterProgress)
        mutateBook(bookId, deferred = true) {
            it.copy(
                readingAnchor = if (it.chapterIndex == safeChapter) it.readingAnchor else null,
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
        if (_state.value.backup.busy) {
            _state.update { it.copy(message = "Espera a que termine la copia antes de borrar libros.") }
            return
        }
        val book = _state.value.library.books.firstOrNull { it.id == bookId } ?: return
        mutateBook(bookId) { it.copy(deletedAt = System.currentTimeMillis()) }
        _state.update { it.copy(message = "Libro enviado a la papelera durante 30 días") }
        if (_state.value.activeBookId == bookId) closeBook()
    }

    fun setBookStatus(ids: Set<String>, status: ReadingStatus) = editBooks(ids) { it.copy(status = status) }
    fun favoriteBooks(ids: Set<String>, favorite: Boolean) = editBooks(ids) { it.copy(favorite = favorite) }
    fun moveBooks(ids: Set<String>, shelf: String?) = editBooks(ids) { it.copy(collectionId = shelf) }
    fun trashBooks(ids: Set<String>) { if (!_state.value.backup.busy) editBooks(ids) { it.copy(deletedAt = System.currentTimeMillis()) } }
    fun restoreBooks(ids: Set<String>) = editBooks(ids) { it.copy(deletedAt = 0L) }
    private fun editBooks(ids: Set<String>, edit: (Book) -> Book) = mutateLibrary { library ->
        library.copy(books = library.books.map { if (it.id in ids) edit(it).copy(modifiedAt = System.currentTimeMillis()) else it })
    }
    fun renameCollection(id: String, name: String, color: Int) {
        if (name.isBlank()) return
        mutateLibrary { it.copy(collections = it.collections.map { shelf -> if (shelf.id == id) shelf.copy(name = name.trim(), colorIndex = color.coerceIn(0, 5)) else shelf }) }
    }
    fun updateMetadata(id: String, title: String, author: String, series: String, volume: String, tags: List<String>) {
        if (title.isBlank()) return
        mutateBook(id) { it.copy(title = title.trim(), author = author.trim(), series = series.trim(), volume = volume.trim(), tags = tags.map(String::trim).filter(String::isNotBlank).distinct()) }
    }
    fun deletePermanently(ids: Set<String>) {
        if (_state.value.backup.busy) return
        val removed = _state.value.library.books.filter { it.id in ids && it.deletedAt > 0 }
        mutateLibrary { it.copy(books = it.books - removed.toSet()) }
        LibraryWrites.enqueue { removed.forEach(repository::deleteBook) }
    }
    private fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
        deletePermanently(_state.value.library.books.filter { it.deletedAt in 1..cutoff }.map { it.id }.toSet())
    }
    fun saveVocabulary(word: VocabularyWord) = mutateLibrary {
        it.copy(vocabulary = it.vocabulary.filterNot { old -> old.word.equals(word.word, true) && old.quote == word.quote } + word)
    }
    fun deleteVocabulary(id: String) = mutateLibrary { it.copy(vocabulary = it.vocabulary.filterNot { word -> word.id == id }) }

    fun updateBookCover(bookId: String, uri: Uri) {
        if (_state.value.backup.busy) {
            _state.update { it.copy(message = "Espera a que termine la copia antes de cambiar portadas.") }
            return
        }
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
            book.copy(annotations = book.annotations.filterNot { it.id == annotationId }, annotationTombstones = book.annotationTombstones + annotationId)
        }

    fun addReadingTime(bookId: String, elapsedMillis: Long) {
        if (elapsedMillis < 1_000L) return
        val now = java.time.ZonedDateTime.now()
        val today = now.toLocalDate().toString()
        val sinceMidnight = java.time.Duration.between(now.toLocalDate().atStartOfDay(now.zone), now).toMillis()
        val todayMillis = minOf(elapsedMillis, sinceMidnight)
        val yesterday = now.toLocalDate().minusDays(1).toString()
        mutateLibrary { library ->
            val days = library.readingDays.associate { it.date to it.millis }.toMutableMap()
            days[today] = (days[today] ?: 0L) + todayMillis
            if (elapsedMillis > todayMillis) days[yesterday] = (days[yesterday] ?: 0L) + elapsedMillis - todayMillis
            library.copy(books = library.books.map { if (it.id == bookId) it.copy(readingTimeMillis = it.readingTimeMillis + elapsedMillis) else it },
                readingDays = days.toSortedMap().map { ReadingDay(it.key, it.value) })
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

    fun clearDriveBackups() { _state.update { it.copy(backup = it.backup.copy(driveBackups = emptyList(), lastResult = null)) } }
    fun selectDriveAccount(name: String?) {
        if (_state.value.backup.busy || _state.value.backup.accountName == name) return
        _state.update { it.copy(backup = it.backup.copy(accountName = name, driveBackups = emptyList(), lastResult = null, error = null)) }
    }
    fun clearBackupError() { io.luma.reader.backup.BackupJobs.preferences(getApplication()).edit().remove("lastError").apply(); _state.update { it.copy(backup = it.backup.copy(error = null)) } }
    fun backupError(message: String) { _state.update { it.copy(backup = it.backup.copy(error = message)) } }

    fun exportBackup(uri: Uri) {
        if (_state.value.backup.busy || _state.value.importing) return
        runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        flushPersistence()
        io.luma.reader.backup.BackupJobs.enqueue(getApplication(), "local", uri = uri.toString())
    }

    fun restoreBackup(uri: Uri) = backupOperation("Restaurando copia local", restoring = true) {
        val service = BackupRepository(getApplication())
        val file = service.copyFrom(uri)
        try { commitRestored(service.restore(file, _state.value.library)) } finally { file.delete() }
        "Biblioteca recuperada; los libros actuales también se conservan"
    }

    fun exportNotes(uri: Uri) = backupOperation("Exportando notas") {
        val text = buildString {
            appendLine("# Notas de Nova Reader")
            _state.value.library.books.forEach { book ->
                if (book.annotations.isNotEmpty()) {
                    appendLine(); appendLine("## ${book.title}"); appendLine(book.author)
                    book.annotations.forEach { note ->
                        appendLine(); appendLine("### Capítulo ${note.chapterIndex + 1}")
                        if (note.selectedText.isNotBlank()) note.selectedText.lines().forEach { appendLine("> $it") }
                        if (note.note.isNotBlank()) { appendLine(); appendLine(note.note) }
                    }
                }
            }
        }
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
            ?: error("No se pudieron guardar las notas")
        "Notas exportadas"
    }

    fun saveToDrive(token: String) {
        if (_state.value.backup.busy || _state.value.importing) return
        val account = _state.value.backup.accountName ?: return backupError("Elige una cuenta de Google")
        flushPersistence()
        io.luma.reader.backup.BackupJobs.memoryToken = account to token
        io.luma.reader.backup.BackupJobs.enqueue(getApplication(), "drive", account = account)
    }
    fun scheduleBackup(hours: Long, wifi: Boolean) {
        val prefs = io.luma.reader.backup.BackupJobs.preferences(getApplication())
        if (hours > 0 && prefs.getString("destination", null) == "drive" && prefs.getString("account", null) != _state.value.backup.accountName) {
            backupError("Guarda primero una copia en la cuenta elegida antes de programarla"); return
        }
        runCatching { io.luma.reader.backup.BackupJobs.schedule(getApplication(), hours, wifi) }
            .onSuccess { _state.update { it.copy(backup = it.backup.copy(scheduleHours = hours, wifiOnly = wifi)) } }
            .onFailure { backupError(it.message ?: "Guarda primero una copia") }
    }
    fun cancelBackup() { viewModelScope.launch { io.luma.reader.backup.BackupJobs.cancel(getApplication()) } }
    private fun observeBackupJobs() {
        val context = getApplication<Application>()
        val prefs = io.luma.reader.backup.BackupJobs.preferences(context)
        _state.update { it.copy(backup = it.backup.copy(accountName = prefs.getString("account", null),
            lastSuccess = prefs.getLong("lastSuccess", 0), scheduleHours = prefs.getLong("hours", 0),
            wifiOnly = prefs.getBoolean("wifi", true), destination = prefs.getString("destination", null))) }
        viewModelScope.launch {
            androidx.work.WorkManager.getInstance(context).getWorkInfosByTagFlow(io.luma.reader.backup.BackupJobs.TAG).collect { jobs ->
                val running = jobs.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                val queued = jobs.firstOrNull { it.state == androidx.work.WorkInfo.State.ENQUEUED && it.periodicityInfo == null }
                val active = running ?: queued
                if (!_state.value.backup.restoring) _state.update { state -> state.copy(backup = state.backup.copy(
                    busy = active != null, operation = active?.progress?.getString("label") ?: "Copia en espera",
                    progress = active?.progress?.getFloat("progress", -1f)?.takeIf { it >= 0 },
                    lastSuccess = prefs.getLong("lastSuccess", 0), destination = prefs.getString("destination", null),
                    error = if (active == null) prefs.getString("lastError", null) else null,
                    lastResult = if (active == null && prefs.getLong("lastSuccess", 0) > state.backup.lastSuccess) "Copia completada" else state.backup.lastResult
                )) }
            }
        }
    }
    fun prepareSync(token: String) = backupOperation("Comparando progreso y notas", restoring = true) {
        val preview = io.luma.reader.drive.LibrarySync.download(getApplication(), token, _state.value.library)
        syncToken = token
        syncPreview.value = preview
        "Revisa los avances antes de sincronizar"
    }
    fun cancelSync() { syncPreview.value = null; syncToken = null }
    fun applySync(remoteIds: Set<String>) {
        val preview = syncPreview.value ?: return
        val token = syncToken ?: return
        backupOperation("Sincronizando progreso y notas", restoring = true) {
            val merged = io.luma.reader.drive.LibrarySync.applyChoices(_state.value.library, preview, remoteIds)
            repository.save(merged)
            _state.update { it.copy(library = merged) }
            io.luma.reader.drive.LibrarySync.upload(getApplication(), token, merged)
            cancelSync()
            "Progreso, marcadores y notas sincronizados"
        }
    }
    fun listDriveBackups(token: String) = backupOperation("Buscando copias en Google Drive") {
        val files = DriveBackupClient(token).list()
        _state.update { it.copy(backup = it.backup.copy(driveBackups = files)) }
        if (files.isEmpty()) "Todavía no hay copias de esta app en Drive" else "Elige la copia que quieres recuperar"
    }
    fun restoreFromDrive(token: String, backup: DriveBackup) = backupOperation("Descargando copia de Drive", restoring = true) {
        val file = File(getApplication<Application>().cacheDir, "drive-restore-${java.util.UUID.randomUUID()}.zip")
        try {
            DriveBackupClient(token).download(backup, file, ::backupProgress)
            updateBackupOperation("Recuperando libros y progreso")
            commitRestored(BackupRepository(getApplication()).restore(file, _state.value.library))
        } finally { file.delete() }
        "Biblioteca recuperada desde Drive"
    }

    private fun updateBackupOperation(label: String) { _state.update { it.copy(backup = it.backup.copy(operation = label, progress = null)) } }
    private fun backupProgress(progress: Float) { _state.update { it.copy(backup = it.backup.copy(progress = progress)) } }

    private fun backupOperation(label: String, restoring: Boolean = false, operation: suspend () -> String) {
        if (_state.value.backup.busy || _state.value.importing || _state.value.loading) return
        flushPersistence()
        _state.update { it.copy(backup = it.backup.copy(busy = true, restoring = restoring, operation = label, progress = null, error = null, lastResult = null)) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { LibraryWrites.awaitIdle(); operation() }
                _state.update { it.copy(backup = it.backup.copy(lastResult = result)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { backupError(e.message ?: "No se pudo completar la copia") }
            finally { _state.update { it.copy(backup = it.backup.copy(busy = false, restoring = false, progress = null)) } }
        }
    }

    private fun commitRestored(restored: LibrarySnapshot) {
        io.luma.reader.audio.NarrationPlayer.command(getApplication(), "stop")
        val current = _state.value.library
        try { repository.save(restored) }
        catch (error: Throwable) {
            val previousPaths = current.books.map { it.epubPath }.toSet()
            restored.books.filterNot { it.epubPath in previousPaths }.forEach { File(it.epubPath).parentFile?.deleteRecursively() }
            throw error
        }
        _state.update { it.copy(library = restored, activeBookId = null, readerTarget = null) }
        val newPaths = restored.books.map { it.epubPath }.toSet()
        current.books.filterNot { it.epubPath in newPaths }.forEach(repository::deleteBook)
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

    private fun mutateBook(bookId: String, deferred: Boolean = false, transform: (Book) -> Book) {
        mutateLibrary(deferred) { library ->
            library.copy(books = library.books.map { if (it.id == bookId) transform(it).copy(modifiedAt = System.currentTimeMillis()) else it })
        }
    }

    private fun mutateLibrary(deferred: Boolean = false, transform: (LibrarySnapshot) -> LibrarySnapshot) {
        if (_state.value.loading || _state.value.backup.restoring) return
        val updated = transform(_state.value.library)
        _state.update { it.copy(library = updated) }
        if (!deferred) flushPersistence()
        else if (saveJob?.isActive != true) saveJob = viewModelScope.launch { delay(1_500); flushPersistence() }
    }
}
