package io.luma.reader.model

import androidx.compose.runtime.Immutable

@Immutable
data class Chapter(
    val title: String,
    val href: String,
    val mediaType: String = "application/xhtml+xml"
)

@Immutable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val epubPath: String,
    val contentPath: String,
    val coverPath: String? = null,
    val chapters: List<Chapter>,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L,
    val chapterIndex: Int = 0,
    val chapterProgress: Float = 0f,
    val progress: Float = 0f,
    val collectionId: String? = null,
    val favorite: Boolean = false,
    val totalWords: Int = 0,
    val readerSettings: ReaderSettings? = null,
    val review: String = "",
    val rating: Int = 0,
    val annotations: List<ReaderAnnotation> = emptyList(),
    val readingTimeMillis: Long = 0L
)

@Immutable
data class ReaderAnnotation(
    val id: String,
    val chapterIndex: Int,
    val selectedText: String = "",
    val note: String = "",
    val blockIndex: Int = -1,
    val endBlockIndex: Int = blockIndex,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class BookCollection(
    val id: String,
    val name: String,
    val colorIndex: Int = 0
)

enum class LibrarySort(val label: String) {
    RECENT("Recientes"),
    TITLE("Título"),
    AUTHOR("Autor"),
    PROGRESS("Progreso")
}

enum class ReaderTheme(val label: String) {
    PAPER("Papel"),
    SEPIA("Sepia"),
    SAND("Arena"),
    MINT("Menta"),
    LAVENDER("Lavanda"),
    ROSE("Rosa"),
    SKY("Cielo"),
    SLATE("Pizarra"),
    NIGHT("Noche"),
    OLED("OLED")
}

enum class ReaderFont(val label: String, val css: String) {
    SERIF("Literaria", "Georgia, 'Times New Roman', serif"),
    GARAMOND("Clásica", "Garamond, 'EB Garamond', Georgia, serif"),
    BASKERVILLE("Elegante", "Baskerville, 'Libre Baskerville', Georgia, serif"),
    PALATINO("Humanista", "Palatino, 'Palatino Linotype', serif"),
    SANS("Moderna", "Arial, Helvetica, sans-serif"),
    SYSTEM("Sistema", "system-ui, -apple-system, sans-serif"),
    DYSLEXIC("Legible", "Verdana, Arial, sans-serif"),
    MONO("Monoespaciada", "'Courier New', monospace")
}

enum class AppThemeMode(val label: String) {
    SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro")
}

enum class AppAccent(val label: String) {
    VIOLET("Violeta"), OCEAN("Océano"), FOREST("Bosque")
}

@Immutable
data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val accent: AppAccent = AppAccent.VIOLET,
    val libraryColumns: Int = 2,
    val includeShelvedBooks: Boolean = true
)

enum class ReaderAlignment(val label: String, val css: String) {
    START("Izquierda", "left"),
    JUSTIFY("Justificado", "justify")
}

enum class ReadingMode(val label: String) {
    SCROLL("Desplazamiento"),
    PAGED("Paginado")
}

@Immutable
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val font: ReaderFont = ReaderFont.SERIF,
    val alignment: ReaderAlignment = ReaderAlignment.START,
    val readingMode: ReadingMode = ReadingMode.SCROLL,
    val fontSize: Int = 19,
    val lineHeight: Float = 1.65f,
    val horizontalMargin: Int = 22,
    val brightness: Float = 1f,
    val extraDim: Float = 0f,
    val keepScreenOn: Boolean = true,
    val ttsRate: Float = 1f,
    val ttsPitch: Float = 1f,
    val ttsEngine: String? = null,
    val ttsLanguageTag: String = "system",
    val ttsVoiceName: String? = null,
    val ttsVolume: Float = 1f,
    val ttsSentencePauseMs: Int = 120,
    val ttsParagraphPauseMs: Int = 320,
    val ttsChapterPauseMs: Int = 700,
    val ttsAutoAdvance: Boolean = true
)

@Immutable
data class LibrarySnapshot(
    val books: List<Book> = emptyList(),
    val collections: List<BookCollection> = emptyList(),
    val settings: ReaderSettings = ReaderSettings(),
    val appSettings: AppSettings = AppSettings(),
    val sort: LibrarySort = LibrarySort.RECENT,
    val gridView: Boolean = true
)
