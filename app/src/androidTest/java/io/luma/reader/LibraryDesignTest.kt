package io.luma.reader

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.luma.reader.data.LibraryRepository
import io.luma.reader.model.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class LibraryDesignTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun cleanUp() {
        scenario?.close()
        context.getSharedPreferences("luma_library", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "library/design-test").deleteRecursively()
    }

    @Test fun filtersSearchAndNotesUseTheExistingLibrary() {
        val books = seed()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("book_${books[0].id}"), 30_000)
        screenshot("biblioteca")
        compose.onNodeWithText("Pendientes").performClick()
        compose.onNodeWithTag("book_${books[1].id}").assertExists()
        compose.onNodeWithTag("book_${books[0].id}").assertDoesNotExist()
        compose.onNodeWithText("Favoritos").performClick()
        compose.onNodeWithTag("book_${books[0].id}").assertExists()
        compose.onNodeWithTag("book_${books[1].id}").assertDoesNotExist()
        compose.onNodeWithText("Todos").performClick()
        compose.onNodeWithTag("library_search").performTextInput("Aranda")
        compose.onNodeWithTag("book_${books[1].id}").assertExists()
        compose.onNodeWithTag("book_${books[0].id}").assertDoesNotExist()
        compose.onNodeWithText("Notas").performClick()
        compose.onNodeWithText("La luz atraviesa el jardín.").assertExists()
        compose.onNodeWithText("Recordar este pasaje").assertExists()
        screenshot("notas")
        compose.onNodeWithText("Estantes").performClick()
        screenshot("estantes")
        compose.onNodeWithText("Mis favoritos").performClick()
        compose.onNodeWithTag("book_${books[0].id}").assertExists()
        compose.onNodeWithContentDescription("Volver a estantes").performClick()
        compose.onNodeWithContentDescription("Nuevo estante").assertExists()
    }

    @Test fun longPressOpensTheSameOptionsAsTheMenuButton() {
        val books = seed()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("book_${books[0].id}"), 30_000)
        compose.onNodeWithTag("book_${books[0].id}").performTouchInput { longClick() }
        compose.onNodeWithText("Cambiar portada").assertExists()
        screenshot("opciones-pulsacion-larga")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Opciones de ${books[0].title}").performClick()
        compose.onNodeWithText("Cambiar portada").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("continue_reading").performTouchInput { longClick() }
        compose.onNodeWithText("Cambiar portada").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Estantes").performClick()
        compose.onNodeWithText("Mis favoritos").performClick()
        compose.onNodeWithTag("book_${books[0].id}").performTouchInput { longClick() }
        compose.onNodeWithText("Cambiar portada").assertExists()
    }

    @Test fun emptyLibraryKeepsImportAndNavigationAvailable() {
        context.getSharedPreferences("luma_library", Context.MODE_PRIVATE).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasText("Importar un libro"), 30_000)
        compose.onNodeWithText("Importar un libro").assertExists()
        compose.onNodeWithText("Notas").performClick()
        compose.onNodeWithText("Guarda lo que te inspira").assertExists()
        compose.onNodeWithText("Biblioteca").performClick()
        compose.onNodeWithContentDescription("Abrir ajustes generales").performClick()
        compose.onNodeWithTag("general_settings_sheet").assertExists()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(context.getExternalFilesDir(null), "design-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun seed(): List<Book> {
        val root = File(context.filesDir, "library/design-test").apply { mkdirs() }
        val content = File(root, "content").apply { mkdirs() }
        File(content, "chapter.xhtml").writeText("<html><body><p>La luz atraviesa el jardín.</p></body></html>")
        val epub = File(root, "book.epub").apply { writeBytes(byteArrayOf(1)) }
        val book = Book("design-reading", "El jardín de la luz", "Clara Montes", epub.path, content.path,
            chapters = listOf(Chapter("Inicio", "chapter.xhtml")), progress = .68f, favorite = true,
            collectionId = "design-shelf", annotations = listOf(ReaderAnnotation("design-note", 0,
                selectedText = "La luz atraviesa el jardín.", note = "Recordar este pasaje")))
        val pending = book.copy(id = "design-pending", title = "La ciudad invisible", author = "Daniel Aranda",
            progress = 0f, favorite = false, collectionId = null, annotations = emptyList())
        val books = listOf(book, pending,
            pending.copy(id = "design-sea", title = "El mar en calma", author = "Lucía Ferrer"),
            pending.copy(id = "design-days", title = "Los días azules", author = "Mario Beltrán"),
            pending.copy(id = "design-olive", title = "Bajo el olivo", author = "Inés Vidal"),
            pending.copy(id = "design-sky", title = "Cielo de invierno", author = "Ana Torres"))
        LibraryRepository(context).save(LibrarySnapshot(books = books,
            collections = listOf(BookCollection("design-shelf", "Mis favoritos"))))
        return books
    }
}
