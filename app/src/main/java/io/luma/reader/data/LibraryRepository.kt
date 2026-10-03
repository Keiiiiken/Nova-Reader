package io.luma.reader.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.luma.reader.model.Book
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LibraryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("luma_library", Context.MODE_PRIVATE)

    fun load(): LibrarySnapshot {
        val raw = preferences.getString(KEY_STATE, null) ?: return LibrarySnapshot()
        return runCatching { decode(JSONObject(raw)) }.getOrDefault(LibrarySnapshot())
    }

    @Synchronized
    fun save(snapshot: LibrarySnapshot) {
        preferences.edit().putString(KEY_STATE, encode(snapshot).toString()).apply()
    }

    fun deleteBook(book: Book) {
        File(book.epubPath).parentFile?.takeIf {
            it.canonicalPath.startsWith(File(appContext.filesDir, "library").canonicalPath)
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
    }

    private fun encodeBook(book: Book) = JSONObject().apply {
        put("id", book.id)
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
                put(JSONObject().put("title", it.title).put("href", it.href).put("mediaType", it.mediaType))
            }
        })
    }

    private fun decode(json: JSONObject): LibrarySnapshot {
        val books = json.optJSONArray("books").mapObjects(::decodeBook)
            .filter { File(it.epubPath).exists() && File(it.contentPath).exists() }
        val collections = json.optJSONArray("collections").mapObjects {
            BookCollection(it.getString("id"), it.getString("name"), it.optInt("color"))
        }
        return LibrarySnapshot(
            books = books,
            collections = collections,
            settings = decodeSettings(json.optJSONObject("settings") ?: JSONObject()),
            appSettings = decodeAppSettings(json.optJSONObject("appSettings") ?: JSONObject()),
            sort = json.optEnum("sort", LibrarySort.RECENT),
            gridView = json.optBoolean("grid", true)
        )
    }

    private fun decodeBook(json: JSONObject): Book = Book(
        id = json.getString("id"),
        title = json.optString("title", "Libro sin título"),
        author = json.optString("author", "Autor desconocido"),
        epubPath = json.getString("epubPath"),
        contentPath = json.getString("contentPath"),
        coverPath = json.optString("coverPath").takeIf { it.isNotBlank() && it != "null" },
        chapters = json.optJSONArray("chapters").mapObjects {
            Chapter(it.optString("title"), it.getString("href"), it.optString("mediaType"))
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

    private fun encodeAppSettings(settings: AppSettings) = JSONObject().apply {
        put("themeMode", settings.themeMode.name)
        put("accent", settings.accent.name)
        put("libraryColumns", settings.libraryColumns)
        put("includeShelvedBooks", settings.includeShelvedBooks)
    }

    private fun decodeAppSettings(json: JSONObject) = AppSettings(
        themeMode = json.optEnum("themeMode", AppThemeMode.SYSTEM),
        accent = json.optEnum("accent", AppAccent.BLUE),
        libraryColumns = json.optInt("libraryColumns", 3).coerceIn(2, 3),
        includeShelvedBooks = json.optBoolean("includeShelvedBooks", true)
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

    companion object {
        private const val KEY_STATE = "library_state_v1"
    }
}
