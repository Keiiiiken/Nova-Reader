package io.luma.reader.data

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.luma.reader.model.*
import io.luma.reader.model.BookCollection
import io.luma.reader.model.Chapter
import io.luma.reader.model.AppAccent
import io.luma.reader.model.AppSettings
import io.luma.reader.model.AppThemeMode
import io.luma.reader.model.LibrarySnapshot
import io.luma.reader.model.LibrarySort
import io.luma.reader.model.ReaderFont
import io.luma.reader.model.ReaderAlignment
import io.luma.reader.model.ReaderSettings
import io.luma.reader.model.ReaderTheme
import io.luma.reader.model.ReadingMode
import io.luma.reader.model.ReaderAnnotation
import io.luma.reader.model.ReadingAnchor
import io.luma.reader.model.ReaderBookmark
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LibraryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("luma_library", Context.MODE_PRIVATE)

    private var lastSaved: LibrarySnapshot? = null

    fun load(): LibrarySnapshot = synchronized(databaseLock) {
        LibraryDatabase(appContext).use { helper ->
            val db = helper.writableDatabase
            val metadata = db.rawQuery("SELECT payload FROM metadata WHERE id=1", null).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            val result = if (metadata == null) {
                // Keep the old preference until migration has committed successfully.
                val legacy = preferences.getString(KEY_STATE, null)
                val snapshot = if (legacy == null) LibrarySnapshot() else decodeSnapshot(legacy)
                writeSnapshot(db, snapshot, null)
                snapshot
            } else {
                val json = JSONObject(metadata)
                val books = JSONArray()
                db.rawQuery("SELECT payload FROM books ORDER BY ordinal", null).use { cursor ->
                    while (cursor.moveToNext()) books.put(JSONObject(cursor.getString(0)))
                }
                json.put("books", books)
                decode(json)
            }
            lastSaved = result
            result
        }
    }

    fun save(snapshot: LibrarySnapshot) = synchronized(databaseLock) {
        LibraryDatabase(appContext).use { helper ->
            writeSnapshot(helper.writableDatabase, snapshot, lastSaved)
            lastSaved = snapshot
        }
    }

    private fun writeSnapshot(db: android.database.sqlite.SQLiteDatabase, snapshot: LibrarySnapshot, previous: LibrarySnapshot?) {
        db.beginTransaction()
        try {
            val oldBooks = previous?.books?.associateBy { it.id }.orEmpty()
            val ids = snapshot.books.map { it.id }.toSet()
            db.rawQuery("SELECT id FROM books", null).use { cursor ->
                val removed = mutableListOf<String>()
                while (cursor.moveToNext()) if (cursor.getString(0) !in ids) removed += cursor.getString(0)
                removed.forEach { db.delete("books", "id=?", arrayOf(it)) }
            }
            snapshot.books.forEachIndexed { index, book ->
                if (oldBooks[book.id] != book || previous?.books?.getOrNull(index)?.id != book.id) {
                    db.insertWithOnConflict("books", null, ContentValues().apply {
                        put("id", book.id)
        put("format", book.format.name); put("fingerprint", book.fingerprint)
        put("status", book.status?.name); put("series", book.series); put("volume", book.volume)
        put("tags", JSONArray(book.tags)); put("deletedAt", book.deletedAt); put("modifiedAt", book.modifiedAt)
        put("annotationTombstones", JSONArray(book.annotationTombstones.toList()))
        put("bookmarkTombstones", JSONArray(book.bookmarkTombstones.toList())); put("payload", encodeBook(book).toString()); put("ordinal", index)
                    }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
                }
            }
            val metadata = snapshot.copy(books = emptyList())
            if (previous == null || previous.copy(books = emptyList()) != metadata) {
                db.insertWithOnConflict("metadata", null, ContentValues().apply {
                    put("id", 1); put("payload", encode(metadata).toString())
                }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    internal fun encodeSnapshot(snapshot: LibrarySnapshot): String = encode(snapshot).toString()
    internal fun decodeSnapshot(payload: String, checkFiles: Boolean = true): LibrarySnapshot = decode(JSONObject(payload), checkFiles)

    fun deleteBook(book: Book) {
        File(book.epubPath).parentFile?.takeIf {
            it.canonicalPath.startsWith(File(appContext.filesDir, "library").canonicalPath + File.separator)
        }?.deleteRecursively()
    }

    fun createCollection(name: String, colorIndex: Int): BookCollection =
        BookCollection(UUID.randomUUID().toString(), name.trim(), colorIndex)

    fun copyCustomCover(book: Book, uri: Uri): String {
        val libraryRoot = File(appContext.filesDir, "library").canonicalFile
        val bookRoot = (File(book.epubPath).parentFile
            ?: error("No se encontró la carpeta privada del libro")).canonicalFile
        require(
            bookRoot.path == libraryRoot.path ||
                bookRoot.path.startsWith(libraryRoot.path + File.separator)
        ) { "La portada solo puede guardarse dentro de la biblioteca" }

        val temporary = File(bookRoot, "custom-cover.pending")
        val destination = File(bookRoot, "custom-cover-${System.nanoTime()}.image")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use(input::copyTo)
            } ?: error("No se pudo abrir la imagen seleccionada")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "El archivo seleccionado no es una imagen compatible"
            }
            temporary.copyTo(destination, overwrite = true)
            book.coverPath?.let(::File)
                ?.takeIf {
                    it.parentFile?.canonicalFile == bookRoot &&
                        it.name.startsWith("custom-cover-") &&
                        it.canonicalFile != destination.canonicalFile
                }
                ?.delete()
            return destination.absolutePath
        } finally {
            temporary.delete()
        }
    }

    private fun encode(state: LibrarySnapshot) = JSONObject().apply {
        put("books", JSONArray().apply { state.books.forEach { put(encodeBook(it)) } })
        put("collections", JSONArray().apply {
            state.collections.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.colorIndex))
            }
        })
        put("settings", encodeSettings(state.settings))
        put("appSettings", encodeAppSettings(state.appSettings))
        put("sort", state.sort.name)
        put("grid", state.gridView)
        put("vocabulary", JSONArray().apply { state.vocabulary.forEach { word -> put(JSONObject()
            .put("id", word.id).put("word", word.word).put("definition", word.definition)
            .put("quote", word.quote).put("bookTitle", word.bookTitle).put("createdAt", word.createdAt)) } })
        put("readingDays", JSONArray().apply { state.readingDays.forEach { put(JSONObject().put("date", it.date).put("millis", it.millis)) } })
    }

    private fun encodeBook(book: Book) = JSONObject().apply {
        put("id", book.id)
        put("format", book.format.name); put("fingerprint", book.fingerprint)
        put("status", book.status?.name); put("series", book.series); put("volume", book.volume)
        put("tags", JSONArray(book.tags)); put("deletedAt", book.deletedAt); put("modifiedAt", book.modifiedAt)
        put("annotationTombstones", JSONArray(book.annotationTombstones.toList()))
        put("bookmarkTombstones", JSONArray(book.bookmarkTombstones.toList()))
        put("title", book.title)
        put("author", book.author)
        put("epubPath", book.epubPath)
        put("contentPath", book.contentPath)
        put("coverPath", book.coverPath)
        put("addedAt", book.addedAt)
        put("lastOpenedAt", book.lastOpenedAt)
        put("chapterIndex", book.chapterIndex)
        put("chapterProgress", book.chapterProgress.toDouble())
        put("progress", book.progress.toDouble())
        put("collectionId", book.collectionId)
        put("favorite", book.favorite)
        put("totalWords", book.totalWords)
        put("readerSettings", book.readerSettings?.let(::encodeSettings) ?: JSONObject.NULL)
        put("review", book.review)
        put("rating", book.rating)
        put("readingTimeMillis", book.readingTimeMillis)
        put("readingAnchor", book.readingAnchor?.let(::encodeAnchor))
        put("bookmarks", JSONArray().apply { book.bookmarks.forEach { bookmark ->
            put(JSONObject().apply {
                put("id", bookmark.id); put("chapterIndex", bookmark.chapterIndex)
                put("progress", bookmark.progress.toDouble()); put("anchor", bookmark.anchor?.let(::encodeAnchor))
                put("label", bookmark.label); put("createdAt", bookmark.createdAt)
            })
        } })
        put("annotations", JSONArray().apply {
            book.annotations.forEach { annotation ->
                put(JSONObject().apply {
                    put("id", annotation.id)
                    put("chapterIndex", annotation.chapterIndex)
                    put("selectedText", annotation.selectedText)
                    put("note", annotation.note)
                    put("blockIndex", annotation.blockIndex)
                    put("endBlockIndex", annotation.endBlockIndex)
                    put("startOffset", annotation.startOffset)
                    put("endOffset", annotation.endOffset)
                    put("colorIndex", annotation.colorIndex)
                    put("createdAt", annotation.createdAt)
                })
            }
        })
        put("chapters", JSONArray().apply {
            book.chapters.forEach {
                put(JSONObject().put("title", it.title).put("href", it.href).put("mediaType", it.mediaType).put("wordCount", it.wordCount))
            }
        })
    }

    private fun decode(json: JSONObject, checkFiles: Boolean = true): LibrarySnapshot {
        val books = json.optJSONArray("books").mapObjects(::decodeBook)
            .filter { !checkFiles || (File(it.epubPath).isFile && File(it.contentPath).isDirectory) }
        val collections = json.optJSONArray("collections").mapObjects {
            BookCollection(it.getString("id"), it.getString("name"), it.optInt("color"))
        }
        return LibrarySnapshot(
            books = books,
            collections = collections,
            settings = decodeSettings(json.optJSONObject("settings") ?: JSONObject()),
            appSettings = decodeAppSettings(json.optJSONObject("appSettings") ?: JSONObject()),
            sort = json.optEnum("sort", LibrarySort.RECENT),
            gridView = json.optBoolean("grid", true),
            vocabulary = json.optJSONArray("vocabulary").mapObjects { VocabularyWord(it.getString("id"), it.getString("word"), it.getString("definition"), it.optString("quote"), it.optString("bookTitle"), it.optLong("createdAt")) },
            readingDays = json.optJSONArray("readingDays").mapObjects { ReadingDay(it.getString("date"), it.optLong("millis").coerceAtLeast(0L)) }
        )
    }

    private fun decodeBook(json: JSONObject): Book = Book(
        id = json.getString("id"),
        format = json.optEnum("format", BookFormat.EPUB),
        fingerprint = json.optString("fingerprint"),
        status = runCatching { ReadingStatus.valueOf(json.optString("status")) }.getOrNull(),
        series = json.optString("series"), volume = json.optString("volume"), tags = json.optJSONArray("tags").strings(),
        deletedAt = json.optLong("deletedAt"), modifiedAt = json.optLong("modifiedAt"),
        annotationTombstones = json.optJSONArray("annotationTombstones").strings().toSet(),
        bookmarkTombstones = json.optJSONArray("bookmarkTombstones").strings().toSet(),
        title = json.optString("title", "Libro sin título"),
        author = json.optString("author", "Autor desconocido"),
        epubPath = json.getString("epubPath"),
        contentPath = json.getString("contentPath"),
        coverPath = json.optString("coverPath").takeIf { it.isNotBlank() && it != "null" },
        chapters = json.optJSONArray("chapters").mapObjects {
            Chapter(it.optString("title"), it.getString("href"), it.optString("mediaType"), it.optInt("wordCount"))
        },
        addedAt = json.optLong("addedAt"),
        lastOpenedAt = json.optLong("lastOpenedAt"),
        chapterIndex = json.optInt("chapterIndex"),
        chapterProgress = json.optDouble("chapterProgress").toFloat(),
        progress = json.optDouble("progress").toFloat(),
        collectionId = json.optString("collectionId").takeIf { it.isNotBlank() && it != "null" },
        favorite = json.optBoolean("favorite"),
        totalWords = json.optInt("totalWords"),
        readerSettings = json.optJSONObject("readerSettings")?.let(::decodeSettings),
        review = json.optString("review"),
        rating = json.optInt("rating").coerceIn(0, 5),
        readingTimeMillis = json.optLong("readingTimeMillis").coerceAtLeast(0L),
        readingAnchor = json.optJSONObject("readingAnchor")?.let(::decodeAnchor),
        bookmarks = json.optJSONArray("bookmarks").mapObjects {
            ReaderBookmark(it.getString("id"), it.optInt("chapterIndex"), it.optDouble("progress").toFloat().coerceIn(0f, 1f),
                it.optJSONObject("anchor")?.let(::decodeAnchor), it.optString("label"), it.optLong("createdAt"))
        },
        annotations = json.optJSONArray("annotations").mapObjects { item ->
            ReaderAnnotation(
                id = item.optString("id", UUID.randomUUID().toString()),
                chapterIndex = item.optInt("chapterIndex"),
                selectedText = item.optString("selectedText"),
                note = item.optString("note"),
                blockIndex = item.optInt("blockIndex", -1),
                endBlockIndex = item.optInt("endBlockIndex", item.optInt("blockIndex", -1)),
                startOffset = item.optInt("startOffset"),
                endOffset = item.optInt("endOffset"),
                colorIndex = item.optInt("colorIndex").coerceIn(0, 3),
                createdAt = item.optLong("createdAt", System.currentTimeMillis())
            )
        }
    )

    private fun encodeAnchor(anchor: ReadingAnchor) = JSONObject().apply {
        put("blockIndex", anchor.blockIndex); put("offset", anchor.offset); put("text", anchor.text)
    }
    private fun decodeAnchor(json: JSONObject) = ReadingAnchor(json.optInt("blockIndex"), json.optInt("offset").coerceAtLeast(0), json.optString("text"))

    private fun encodeAppSettings(settings: AppSettings) = JSONObject().apply {
        put("themeMode", settings.themeMode.name)
        put("accent", settings.accent.name)
        put("libraryColumns", settings.libraryColumns)
        put("includeShelvedBooks", settings.includeShelvedBooks)
        put("dailyGoalMinutes", settings.dailyGoalMinutes); put("adaptiveColumns", settings.adaptiveColumns)
    }

    private fun decodeAppSettings(json: JSONObject) = AppSettings(
        themeMode = json.optEnum("themeMode", AppThemeMode.SYSTEM),
        accent = json.optEnum("accent", AppAccent.BLUE),
        libraryColumns = json.optInt("libraryColumns", 3).coerceIn(2, 3),
        includeShelvedBooks = json.optBoolean("includeShelvedBooks", true),
        dailyGoalMinutes = json.optInt("dailyGoalMinutes").coerceIn(0, 240),
        adaptiveColumns = json.optBoolean("adaptiveColumns", true)
    )

    private fun encodeSettings(settings: ReaderSettings) = JSONObject().apply {
        put("theme", settings.theme.name)
        put("font", settings.font.name)
        put("alignment", settings.alignment.name)
        put("readingMode", settings.readingMode.name)
        put("fontSize", settings.fontSize)
        put("lineHeight", settings.lineHeight.toDouble())
        put("margin", settings.horizontalMargin)
        put("brightness", settings.brightness.toDouble())
        put("extraDim", settings.extraDim.toDouble())
        put("keepScreenOn", settings.keepScreenOn)
        put("ttsRate", settings.ttsRate.toDouble())
        put("ttsPitch", settings.ttsPitch.toDouble())
        put("ttsEngine", settings.ttsEngine)
        put("ttsLanguageTag", settings.ttsLanguageTag)
        put("ttsVoiceName", settings.ttsVoiceName)
        put("ttsVolume", settings.ttsVolume.toDouble())
        put("ttsSentencePauseMs", settings.ttsSentencePauseMs)
        put("ttsParagraphPauseMs", settings.ttsParagraphPauseMs)
        put("ttsChapterPauseMs", settings.ttsChapterPauseMs)
        put("ttsAutoAdvance", settings.ttsAutoAdvance)
    }

    private fun decodeSettings(json: JSONObject) = ReaderSettings(
        theme = json.optEnum("theme", ReaderTheme.PAPER),
        font = json.optEnum("font", ReaderFont.SERIF),
        alignment = json.optEnum("alignment", ReaderAlignment.START),
        readingMode = json.optEnum("readingMode", ReadingMode.SCROLL),
        fontSize = json.optInt("fontSize", 19).coerceIn(12, 34),
        lineHeight = json.optDouble("lineHeight", 1.65).toFloat().coerceIn(1.2f, 2.2f),
        horizontalMargin = json.optInt("margin", 22).coerceIn(8, 48),
        brightness = json.optDouble("brightness", 1.0).toFloat().coerceIn(.02f, 1f),
        extraDim = json.optDouble("extraDim", 0.0).toFloat().coerceIn(0f, .8f),
        keepScreenOn = json.optBoolean("keepScreenOn", true),
        ttsRate = json.optDouble("ttsRate", 1.0).toFloat().coerceIn(.5f, 2f),
        ttsPitch = json.optDouble("ttsPitch", 1.0).toFloat().coerceIn(.5f, 2f),
        ttsEngine = json.optString("ttsEngine")
            .takeIf { it.isNotBlank() && it != "null" },
        ttsLanguageTag = json.optString("ttsLanguageTag", "system"),
        ttsVoiceName = json.optString("ttsVoiceName")
            .takeIf { it.isNotBlank() && it != "null" },
        ttsVolume = json.optDouble("ttsVolume", 1.0).toFloat().coerceIn(.1f, 1f),
        ttsSentencePauseMs = json.optInt("ttsSentencePauseMs", 120).coerceIn(0, 1_500),
        ttsParagraphPauseMs = json.optInt("ttsParagraphPauseMs", 320).coerceIn(0, 3_000),
        ttsChapterPauseMs = json.optInt("ttsChapterPauseMs", 700).coerceIn(0, 5_000),
        ttsAutoAdvance = json.optBoolean("ttsAutoAdvance", true)
    )

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(optString(key)) }.getOrDefault(fallback)

    private fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let { add(block(it)) }
            }
        }
    }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

    companion object {
        private val databaseLock = Any()
        private const val KEY_STATE = "library_state_v1"
    }
}
