package io.luma.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class LibraryDesignTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun cleanUp() {
        scenario?.close()
        io.luma.reader.data.LibraryWrites.awaitIdle()
        context.deleteDatabase("nova-library.db")
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
        compose.waitUntilAtLeastOneExists(hasText("Cambiar portada"), 10_000)
        compose.onNodeWithText("Cambiar portada").assertExists()
        screenshot("opciones-pulsacion-larga")
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntilDoesNotExist(hasText("Cambiar portada"), 10_000)
        compose.onNodeWithContentDescription("Opciones de ${books[0].title}").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Cambiar portada"), 10_000)
        compose.onNodeWithText("Cambiar portada").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntilDoesNotExist(hasText("Cambiar portada"), 10_000)
        compose.onNodeWithTag("library_grid").performScrollToNode(hasTestTag("continue_reading"))
        compose.onNodeWithTag("continue_reading").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("Cambiar portada"), 10_000)
        compose.onNodeWithText("Cambiar portada").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntilDoesNotExist(hasText("Cambiar portada"), 10_000)
        compose.onNodeWithText("Estantes").performClick()
        compose.onNodeWithText("Mis favoritos").performClick()
        compose.onNodeWithTag("book_${books[0].id}").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("Cambiar portada"), 10_000)
        compose.onNodeWithText("Cambiar portada").assertExists()
    }

    @Test fun emptyLibraryKeepsImportAndNavigationAvailable() {
        io.luma.reader.data.LibraryWrites.awaitIdle()
        context.deleteDatabase("nova-library.db")
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

    @Test fun closingAndReopeningAnActiveBackupKeepsNavigationAndSettingsUsable() {
        seed()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("book_design-reading"), 30_000)
        openBackup()
        val release = CountDownLatch(1)
        val waiting = CountDownLatch(1)
        val output = File(context.cacheDir, "design-backup.zip")
        lateinit var viewModel: MainViewModel
        try {
            // Hold the real writer queue so the copy remains active during navigation.
            io.luma.reader.data.LibraryWrites.enqueue { waiting.countDown(); release.await(30, TimeUnit.SECONDS) }
            assertTrue(waiting.await(5, TimeUnit.SECONDS))
            scenario!!.onActivity {
                viewModel = ViewModelProvider(it)[MainViewModel::class.java]
                viewModel.exportBackup(Uri.fromFile(output))
            }
            compose.waitUntil(10_000) { viewModel.state.value.backup.busy }
            compose.onNodeWithTag("backup_sheet").performTouchInput { swipeDown(durationMillis = 400) }
            compose.waitUntilDoesNotExist(hasTestTag("backup_sheet"), 10_000)
            compose.onNodeWithText("Notas").performClick()
            compose.onNodeWithText("Recordar este pasaje").assertExists()
            compose.onNodeWithText("Biblioteca").performClick()
            compose.onNodeWithText("Ver").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("backup_sheet"), 10_000)
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitUntilDoesNotExist(hasTestTag("backup_sheet"), 10_000)
            compose.onNodeWithContentDescription("Abrir ajustes generales").performClick()
            compose.onNodeWithText("Oscuro").performClick()
            compose.waitUntil(5_000) { viewModel.state.value.library.appSettings.themeMode == AppThemeMode.DARK }
            compose.onNodeWithTag("close_settings").performClick()
            compose.onNodeWithText("Ver").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("backup_sheet"), 10_000)
            compose.onNodeWithTag("close_backup").performClick()
            compose.waitUntilDoesNotExist(hasTestTag("backup_sheet"), 10_000)
            release.countDown()
            compose.waitUntil(15_000) { !viewModel.state.value.backup.busy }
            assertNull(viewModel.state.value.backup.error)
            assertTrue(output.length() > 0)
            java.util.zip.ZipFile(output).use { assertNotNull(it.getEntry("manifest.json")) }
            io.luma.reader.data.LibraryWrites.awaitIdle()
            assertEquals(AppThemeMode.DARK, LibraryRepository(context).load().appSettings.themeMode)
            compose.onNodeWithText("Estantes").performClick()
            compose.onNodeWithText("Mis favoritos").assertExists()
        } finally { release.countDown(); output.delete() }
    }

    @Test fun driveExplainsItsPermissionBeforeOpeningGoogleAndCanBeCancelled() {
        seed()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("book_design-reading"), 30_000)
        compose.onNodeWithContentDescription("Abrir ajustes generales").performClick()
        screenshot("ajustes-renovados")
        compose.onNodeWithTag("general_settings_sheet").performScrollToNode(hasText("Copias de seguridad y Google Drive"))
        compose.onNodeWithText("Copias de seguridad y Google Drive").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("backup_sheet"), 10_000)
        compose.onNodeWithTag("backup_sheet").performScrollToNode(hasText("Guardar copia en Drive"))
        screenshot("copias-renovadas")
        compose.onNodeWithText("Guardar copia en Drive").performClick()
        compose.onNodeWithText("Tu copia, en tu Drive").assertExists()
        compose.onNodeWithText("Continuar con Google").assertExists()
        screenshot("permiso-drive")
        compose.onNodeWithText("Ahora no").performClick()
        compose.onNodeWithText("Tu copia, en tu Drive").assertDoesNotExist()
        compose.onNodeWithTag("close_backup").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("backup_sheet"), 10_000)
        compose.onNodeWithText("Notas").performClick()
        compose.onNodeWithText("Recordar este pasaje").assertExists()
    }

    private fun openBackup() {
        compose.onNodeWithContentDescription("Abrir ajustes generales").performClick()
        compose.onNodeWithTag("general_settings_sheet").performScrollToNode(hasText("Copias de seguridad y Google Drive"))
        compose.onNodeWithText("Copias de seguridad y Google Drive").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("backup_sheet"), 10_000)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
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
