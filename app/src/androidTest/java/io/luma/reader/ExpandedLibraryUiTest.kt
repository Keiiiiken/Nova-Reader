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
        compose.onNodeWithText("Actividad").performClick()
        compose.onNodeWithText("20 min").performClick()
        compose.waitUntil(8000) { LibraryRepository(context).load().appSettings.dailyGoalMinutes == 20 }
        screenshot("actividad")
        compose.onNodeWithText("Vocabulario").performClick()
        compose.onNodeWithText("Guarda palabras desde el diccionario para encontrarlas aquí.").assertExists()
    }
    @Test fun libraryRemainsUsableInLandscapeWithLargeText() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeShellCommand("settings put system font_scale 1.5").close()
            scenario?.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntilAtLeastOneExists(hasContentDescription("Organizar biblioteca"), 15000)
            compose.onNodeWithContentDescription("Organizar biblioteca").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("select_organize"), 15000)
            compose.onNodeWithTag("select_organize").performClick()
            compose.onNodeWithText("Acciones").assertIsDisplayed()
            screenshot("organizar-horizontal-letra-grande")
        } finally {
            automation.executeShellCommand("settings put system font_scale 1.0").close()
            scenario?.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); Thread.sleep(350)
        val file = File(context.getExternalFilesDir(null), "screenshots/$name.png"); file.parentFile!!.mkdirs()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
