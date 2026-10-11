package io.luma.reader.model

import androidx.compose.runtime.Immutable

@Immutable
data class Chapter(
    val title: String,
    val href: String,
    val mediaType: String = "application/xhtml+xml",
    val wordCount: Int = 0
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
    val readingTimeMillis: Long = 0L,
    val readingAnchor: ReadingAnchor? = null,
    val bookmarks: List<ReaderBookmark> = emptyList(),
    val format: BookFormat = BookFormat.EPUB,
    val fingerprint: String = "",
    val status: ReadingStatus? = null,
    val series: String = "",
    val volume: String = "",
    val tags: List<String> = emptyList(),
    val deletedAt: Long = 0L,
    val modifiedAt: Long = 0L,
    val annotationTombstones: Set<String> = emptySet(),
    val bookmarkTombstones: Set<String> = emptySet()
)

@Immutable
data class ReadingAnchor(val blockIndex: Int, val offset: Int = 0, val text: String = "")

@Immutable
data class ReaderBookmark(
    val id: String,
    val chapterIndex: Int,
    val progress: Float,
    val anchor: ReadingAnchor? = null,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class ReaderTarget(
    val chapterIndex: Int,
    val anchor: ReadingAnchor? = null,
    val progress: Float = 0f,
    val annotationId: String? = null,
    val searchQuery: String? = null,
    val id: String = java.util.UUID.randomUUID().toString()
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
    SERIF("Literaria", "Novaliteraria, serif"),
    GARAMOND("Clásica", "Novaclasica, serif"),
    BASKERVILLE("Elegante", "Novaelegante, serif"),
    PALATINO("Humanista", "Novahumanista, serif"),
    SANS("Moderna", "Novamoderna, serif"),
    SYSTEM("Sistema", "system-ui, -apple-system, sans-serif"),
    DYSLEXIC("Legible", "Novalegible, serif"),
    MONO("Monoespaciada", "Novamono, serif")
}

enum class AppThemeMode(val label: String) {
    SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro")
}

enum class AppAccent(val label: String) {
    BLUE("Azul"), VIOLET("Violeta"), OCEAN("Océano"), FOREST("Bosque")
}

@Immutable
data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val accent: AppAccent = AppAccent.BLUE,
    val libraryColumns: Int = 3,
    val includeShelvedBooks: Boolean = true,
    val dailyGoalMinutes: Int = 0,
    val adaptiveColumns: Boolean = true
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
    val gridView: Boolean = true,
    val vocabulary: List<VocabularyWord> = emptyList(),
    val readingDays: List<ReadingDay> = emptyList()
)


enum class BookFormat { EPUB, PDF, CBZ }
enum class ReadingStatus(val label: String) {
    PENDING("Pendiente"), READING("Leyendo"), FINISHED("Terminado"), ABANDONED("Abandonado")
}
val Book.effectiveStatus: ReadingStatus get() = status ?: when {
    progress >= .995f -> ReadingStatus.FINISHED
    progress > .001f -> ReadingStatus.READING
    else -> ReadingStatus.PENDING
}
fun Book.weightedProgress(chapter: Int, fraction: Float): Float {
    if (chapters.isEmpty()) return 0f
    val index = chapter.coerceIn(chapters.indices)
    val weights = chapters.map { it.wordCount.coerceAtLeast(1).toLong() }
    return ((weights.take(index).sum() + weights[index] * fraction.coerceIn(0f, 1f)) / weights.sum()).toFloat().coerceIn(0f, 1f)
}
@Immutable
data class VocabularyWord(val id: String, val word: String, val definition: String,
    val quote: String = "", val bookTitle: String = "", val createdAt: Long = System.currentTimeMillis())
@Immutable
data class ReadingDay(val date: String, val millis: Long)

val ReaderFont.assetName: String? get() = when (this) {
    ReaderFont.SERIF -> "literaria"
    ReaderFont.GARAMOND -> "clasica"
    ReaderFont.BASKERVILLE -> "elegante"
    ReaderFont.PALATINO -> "humanista"
    ReaderFont.SANS -> "moderna"
    ReaderFont.DYSLEXIC -> "legible"
    ReaderFont.MONO -> "mono"
    ReaderFont.SYSTEM -> null
}
