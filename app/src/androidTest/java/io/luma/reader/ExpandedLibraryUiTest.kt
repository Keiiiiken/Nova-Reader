package io.luma.reader

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.luma.reader.data.LibraryRepository
import io.luma.reader.data.LibraryWrites
import io.luma.reader.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ExpandedLibraryUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var directory: File
    @Before fun seed() {
        LibraryWrites.awaitIdle(); context.deleteDatabase("nova-library.db")
        context.getSharedPreferences("luma_library", 0).edit().clear().commit()
        directory = File(context.filesDir, "library/organize-fixture").apply { mkdirs() }
        val epub = File(directory, "book.epub").apply { writeText("fixture") }
        val content = File(directory, "content").apply { mkdirs() }
        File(content, "one.xhtml").writeText("<html><body><p>Un texto para organizar.</p></body></html>")
        val book = Book("organize", "Mi libro", "Autora", epub.path, content.path, chapters = listOf(Chapter("Uno", "one.xhtml", wordCount = 5)), fingerprint = "fixture", annotations = listOf(ReaderAnnotation("note", 0, selectedText = "Una luciérnaga", note = "Buscar esta nota")))
        LibraryRepository(context).save(LibrarySnapshot(books = listOf(book), collections = listOf(BookCollection("shelf", "Estante original"))))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Organizar biblioteca"), 15000)
    }
    @After fun cleanup() { scenario?.close(); LibraryWrites.awaitIdle(); context.deleteDatabase("nova-library.db"); directory.deleteRecursively() }
    @Test fun bulkStatusTrashAndRestorePreserveBookData() {
        compose.onNodeWithContentDescription("Organizar biblioteca").performClick()
        compose.onNodeWithTag("select_organize").performClick()
        compose.onNodeWithText("Acciones").performClick()
        compose.onNodeWithText("Cambiar estado").performClick()
        compose.onNodeWithText("Terminado").performClick()
        compose.waitUntil(8000) { LibraryRepository(context).load().books.single().status == ReadingStatus.FINISHED }
        screenshot("organizar-biblioteca")
        compose.onNodeWithText("Acciones").performClick(); compose.onNodeWithText("Enviar a papelera").performClick()
        compose.onNodeWithText("Papelera").performClick()
        compose.onNodeWithTag("select_organize").performClick(); compose.onNodeWithText("Acciones").performClick()
        screenshot("papelera")
        compose.onNodeWithText("Restaurar").performClick()
        compose.waitUntil(8000) { LibraryRepository(context).load().books.single().deletedAt == 0L }
        assertEquals("Buscar esta nota", LibraryRepository(context).load().books.single().annotations.single().note)
        assertTrue(File(directory, "book.epub").exists())
    }
    @Test fun notesSearchFiltersWithoutLosingResults() {
        compose.onNodeWithText("Notas", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("notes_search").performTextInput("inexistente")
        compose.onNodeWithText("Una luciérnaga").assertDoesNotExist()
        compose.onNodeWithTag("notes_search").performTextClearance()
        compose.onNodeWithTag("notes_search").performTextInput("luciérnaga")
        compose.onNodeWithText("Una luciérnaga").assertExists()
        screenshot("buscar-notas")
    }
    @Test fun statisticsAndVocabularyAreReachable() {
        compose.onNodeWithContentDescription("Organizar biblioteca").performClick()
        compose.onNodeWithText("Actividad").performScrollTo().performClick()
        compose.onNodeWithText("20 min").performClick()
        compose.waitUntil(8000) { LibraryRepository(context).load().appSettings.dailyGoalMinutes == 20 }
        screenshot("actividad")
        compose.onNodeWithText("Vocabulario").performScrollTo().performClick()
        compose.onNodeWithText("Guarda palabras desde el diccionario para encontrarlas aquí.").assertExists()
    }
    @Test fun libraryRemainsUsableInLandscapeWithLargeText() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("settings put system font_scale 1.5")).use { it.readBytes() }
            scenario?.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(15000) {
                var ready = false
                scenario?.onActivity { ready = it.resources.configuration.let { c -> c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && kotlin.math.abs(c.fontScale - 1.5f) < .01f } }
                ready
            }
            compose.waitUntilAtLeastOneExists(hasContentDescription("Organizar biblioteca"), 15000)
            compose.onNodeWithContentDescription("Organizar biblioteca").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("select_organize"), 15000)
            compose.onNodeWithTag("select_organize").performClick()
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText("Acciones").assertIsDisplayed(); true }.getOrDefault(false) }
            screenshot("organizar-horizontal-letra-grande")
            compose.onNodeWithText("Acciones").assertIsDisplayed()
        } finally {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("settings put system font_scale 1.0")).use { it.readBytes() }
            scenario?.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }
    @Test fun pausedNarrationAllowsManualChapterNavigation() {
        scenario?.close(); LibraryWrites.awaitIdle()
        val library = LibraryRepository(context).load()
        val book = library.books.single()
        File(book.contentPath, "two.xhtml").writeText("<html><body><p>Segundo capítulo para continuar leyendo.</p></body></html>")
        LibraryRepository(context).save(library.copy(books = listOf(book.copy(chapters = book.chapters + Chapter("Dos", "two.xhtml", wordCount = 6)))))
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntilAtLeastOneExists(hasTestTag("book_${book.id}"), 15000)
            compose.onNodeWithTag("book_${book.id}").performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription("Siguiente"), 15000)
            compose.runOnIdle { io.luma.reader.audio.NarrationPlayer.state.value = io.luma.reader.audio.NarrationState(bookId = book.id, paused = true) }
            compose.onNodeWithContentDescription("Siguiente").performClick()
            compose.waitUntil(10000) { LibraryRepository(context).load().books.single().chapterIndex == 1 }
            compose.waitForIdle()
            assertEquals(1, LibraryRepository(context).load().books.single().chapterIndex)
        } finally { compose.runOnIdle { io.luma.reader.audio.NarrationPlayer.state.value = io.luma.reader.audio.NarrationState() } }
    }
    @Test fun pdfReaderSavesPageAndBookmarkAcrossReopening() {
        val source = File(directory, "document.pdf")
        val document = android.graphics.pdf.PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(360, 540, index + 1).create())
                page.canvas.drawColor(android.graphics.Color.rgb(248, 243, 230))
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 24f }
                page.canvas.drawText("Nova Reader · PDF", 28f, 70f, paint)
                paint.textSize = 16f
                page.canvas.drawText("Página ${index + 1}", 28f, 110f, paint)
                document.finishPage(page)
            }
            source.outputStream().use(document::writeTo)
        } finally { document.close() }
        exerciseFixedReader(source, "lector-pdf")
    }
    @Test fun cbzReaderSavesPageAndBookmarkAcrossReopening() {
        val source = File(directory, "comic.cbz")
        val image = android.graphics.Bitmap.createBitmap(360, 540, android.graphics.Bitmap.Config.ARGB_8888)
        java.util.zip.ZipOutputStream(source.outputStream()).use { zip ->
            repeat(2) { index ->
                val canvas = android.graphics.Canvas(image)
                canvas.drawColor(android.graphics.Color.rgb(225, 231, 246))
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(65, 73, 120); textSize = 24f }
                canvas.drawText("Nova Reader · CBZ", 28f, 70f, paint)
                canvas.drawRect(28f, 110f, 332f, 450f, paint)
                paint.color = android.graphics.Color.WHITE
                canvas.drawText("Página ${index + 1}", 80f, 280f, paint)
                zip.putNextEntry(java.util.zip.ZipEntry("page-$index.png"))
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry()
            }
        }
        image.recycle()
        exerciseFixedReader(source, "lector-cbz")
    }
    private fun exerciseFixedReader(source: File, capture: String) {
        scenario?.close(); LibraryWrites.awaitIdle()
        val book = io.luma.reader.importer.BookImporter(context).import(android.net.Uri.fromFile(source))
        try {
            LibraryRepository(context).save(LibrarySnapshot(books = listOf(book)))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntilAtLeastOneExists(hasTestTag("book_${book.id}"), 15000)
            compose.onNodeWithTag("book_${book.id}").performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription("Página 1"), 15000)
            compose.onNodeWithTag("fixed_page_reader").performTouchInput { swipeLeft() }
            compose.waitUntilAtLeastOneExists(hasText("Página 2 de 2"), 10000)
            compose.waitUntilAtLeastOneExists(hasContentDescription("Página 2"), 15000)
            compose.onNodeWithContentDescription("Guardar página").performClick()
            compose.onNodeWithContentDescription("Ampliar página").performClick()
            compose.onNodeWithContentDescription("Reducir zoom").performClick()
            screenshot(capture)
            compose.onNodeWithContentDescription("Volver").performClick()
            compose.waitUntil(10000) { LibraryRepository(context).load().books.single().let { it.chapterIndex == 1 && it.bookmarks.size == 1 } }
            scenario?.close(); LibraryWrites.awaitIdle()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntilAtLeastOneExists(hasTestTag("book_${book.id}"), 15000)
            compose.onNodeWithTag("book_${book.id}").performClick()
            compose.waitUntilAtLeastOneExists(hasText("Página 2 de 2"), 10000)
            compose.onNodeWithContentDescription("Ver marcadores").performClick()
            compose.onNodeWithText("Página 2").assertIsDisplayed()
        } finally {
            scenario?.close(); LibraryWrites.awaitIdle()
            File(book.epubPath).parentFile!!.deleteRecursively()
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); Thread.sleep(350)
        val file = File(context.getExternalFilesDir(null), "screenshots/$name.png"); file.parentFile!!.mkdirs()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
