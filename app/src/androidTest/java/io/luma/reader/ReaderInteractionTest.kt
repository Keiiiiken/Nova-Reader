package io.luma.reader

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.luma.reader.data.LibraryRepository
import io.luma.reader.model.Book
import io.luma.reader.model.Chapter
import io.luma.reader.model.LibrarySnapshot
import io.luma.reader.model.ReaderSettings
import io.luma.reader.model.ReaderTheme
import io.luma.reader.model.ReadingMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ReaderInteractionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun cleanUp() {
        scenario?.close()
        scenario = null
        context.getSharedPreferences("luma_library", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "library/interaction-test").deleteRecursively()
    }

    @Test
    fun verticalReadingScrollsAndPersistsProgressWithoutRecreatingReader() {
        val book = seedLibrary(ReadingMode.SCROLL)
        launchAndOpen(book)

        compose.onNodeWithTag("epub_content").performTouchInput {
            swipeUp(durationMillis = 550)
        }
        compose.waitUntil(8_000) {
            LibraryRepository(context).load().books.firstOrNull()?.chapterProgress
                ?.let { it > .01f } == true
        }

        val saved = LibraryRepository(context).load().books.single()
        assertTrue("El desplazamiento vertical debe avanzar el progreso", saved.chapterProgress > .01f)
        compose.onNodeWithTag("epub_content").assertExists()
    }

    @Test
    fun pagedReadingMovesHorizontallyAndPersistsProgress() {
        val book = seedLibrary(ReadingMode.PAGED)
        launchAndOpen(book)

        compose.onNodeWithTag("epub_content").performTouchInput {
            swipeLeft(durationMillis = 450)
        }
        compose.waitUntil(8_000) {
            LibraryRepository(context).load().books.firstOrNull()?.chapterProgress
                ?.let { it > .001f } == true
        }

        val saved = LibraryRepository(context).load().books.single()
        assertTrue("El modo paginado debe avanzar horizontalmente", saved.chapterProgress > .001f)
        assertEquals(ReadingMode.PAGED, saved.readerSettings?.readingMode)
    }

    @Test
    fun readerEnablesPinchZoomWithoutShowingLegacyButtons() {
        val book = seedLibrary(ReadingMode.SCROLL)
        launchAndOpen(book)

        scenario?.onActivity { activity ->
            val webView = findWebView(activity.window.decorView)
                ?: error("No se encontró el lector WebView")
            assertTrue("El lector debe permitir zoom", webView.settings.supportZoom())
            assertTrue(webView.settings.builtInZoomControls)
            assertFalse("Los botones antiguos de zoom deben permanecer ocultos", webView.settings.displayZoomControls)
        }
    }

    @Test
    fun narrationHighlightMarksTheCurrentParagraphAndFollowsIt() {
        val book = seedLibrary(ReadingMode.SCROLL)
        launchAndOpen(book)

        val applied = evaluateInReader(
            """
                (function() {
                  var blocks = document.querySelectorAll('p,li,blockquote,h1,h2,h3,h4,h5,h6,td,pre,figcaption');
                  var target = Math.min(25, blocks.length - 1);
                  if (!window.LumaNarration || target < 0) return '0';
                  var length = Math.min(18, blocks[target].textContent.length);
                  window.LumaNarration.highlight(target, 0, length, true);
                  return String(document.querySelectorAll('.luma-tts-active').length);
                })();
            """.trimIndent()
        )
        assertEquals("1", applied)
        Thread.sleep(900)
        assertTrue(
            "El lector debe seguir el párrafo narrado",
            evaluateInReader("String(window.scrollY)").toFloat() > 0f
        )
    }

    @Test
    fun selectedTextOpensTheHighlightEditorAndPersistsTheRange() {
        val book = seedLibrary(ReadingMode.SCROLL)
        launchAndOpen(book)

        scenario?.onActivity { activity ->
            val webView = findWebView(activity.window.decorView)
                ?: error("No se encontró el lector WebView")
            webView.evaluateJavascript(
                """
                    (function() {
                      var paragraphs = document.querySelectorAll('p');
                      var startNode = paragraphs[0] && paragraphs[0].firstChild;
                      var endNode = paragraphs[1] && paragraphs[1].firstChild;
                      if (!startNode || !endNode) return false;
                      var range = document.createRange();
                      range.setStart(startNode, 0);
                      range.setEnd(endNode, Math.min(24, endNode.textContent.length));
                      var selection = window.getSelection();
                      selection.removeAllRanges();
                      selection.addRange(range);
                      document.dispatchEvent(new Event('selectionchange'));
                      return true;
                    })();
                """.trimIndent(),
                null
            )
        }

        compose.waitUntilAtLeastOneExists(hasText("Subrayar"), 8_000)
        compose.onNodeWithText("Leer desde aquí").assertExists()
        compose.onNodeWithText("Subrayar").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Subrayar y comentar"), 8_000)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(8_000) {
            LibraryRepository(context).load().books.single().annotations.singleOrNull()
                ?.selectedText?.isNotBlank() == true
        }

        val annotation = LibraryRepository(context).load().books.single().annotations.single()
        assertTrue(annotation.endBlockIndex > annotation.blockIndex)
        assertTrue(annotation.selectedText.startsWith("Párrafo 1"))

        Thread.sleep(500)
        val marks = AtomicReference("0")
        val latch = CountDownLatch(1)
        scenario?.onActivity { activity ->
            findWebView(activity.window.decorView)?.evaluateJavascript(
                "document.querySelectorAll('mark[data-luma-id]').length.toString()"
            ) { result ->
                marks.set(result.trim('"'))
                latch.countDown()
            }
        }
        assertTrue("El WebView debe responder al verificar el subrayado", latch.await(5, TimeUnit.SECONDS))
        assertTrue("El mismo subrayado debe cubrir más de un párrafo", marks.get().toInt() >= 2)
    }

    @Test
    fun extraSwipeAtChapterEndOpensTheNextChapter() {
        val book = seedLibrary(ReadingMode.SCROLL, paragraphCount = 30)
        launchAndOpen(book)

        scenario?.onActivity { activity ->
            findWebView(activity.window.decorView)?.pageDown(true)
        }
        compose.waitUntil(8_000) {
            LibraryRepository(context).load().books.single().chapterProgress >= .98f
        }
        compose.onNodeWithTag("epub_content").performTouchInput {
            swipeUp(durationMillis = 350)
        }
        compose.waitUntil(8_000) {
            LibraryRepository(context).load().books.single().chapterIndex == 1
        }

        assertEquals(1, LibraryRepository(context).load().books.single().chapterIndex)
    }

    @Test
    fun generalSettingsOpenFromTheTopLogo() {
        seedLibrary(ReadingMode.SCROLL)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("book_interaction-test"), 8_000)

        compose.onNodeWithContentDescription("Abrir ajustes generales").performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("general_settings_sheet"), 8_000)
        compose.onNodeWithTag("general_settings_sheet").assertExists()
    }

    @Test
    fun novelSettingsUseOnlyTheLowerHalfForLivePreview() {
        val book = seedLibrary(ReadingMode.SCROLL)
        launchAndOpen(book)

        compose.onNodeWithContentDescription("Apariencia").performClick()
        compose.waitForIdle()

        val sheetBounds = compose.onNodeWithTag("novel_settings_sheet")
            .assertExists()
            .fetchSemanticsNode()
            .boundsInRoot
        val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
        assertTrue(
            "El panel de la novela debe ocupar al menos 45% de la pantalla",
            sheetBounds.height >= screenHeight * .45f
        )
        assertTrue(
            "El panel de la novela no debe superar 62% de la pantalla",
            sheetBounds.height <= screenHeight * .62f
        )
    }

    private fun launchAndOpen(book: Book) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("book_${book.id}"), 8_000)
        compose.onNodeWithTag("book_${book.id}").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("epub_content"), 8_000)
        // Give WebView time to finish layout and compute its scroll range.
        compose.waitForIdle()
        Thread.sleep(1_500)
    }

    private fun seedLibrary(mode: ReadingMode, paragraphCount: Int = 100): Book {
        context.getSharedPreferences("luma_library", Context.MODE_PRIVATE).edit().clear().commit()
        val bookDir = File(context.filesDir, "library/interaction-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val contentDir = File(bookDir, "content/text").apply { mkdirs() }
        val chapterFile = File(contentDir, "long.xhtml")
        chapterFile.writeText(buildLongChapter(paragraphCount))
        File(contentDir, "second.xhtml").writeText(buildLongChapter(8))
        val epubFile = File(bookDir, "book.epub").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val customSettings = ReaderSettings(
            theme = ReaderTheme.PAPER,
            readingMode = mode,
            fontSize = 19,
            lineHeight = 1.65f
        )
        val book = Book(
            id = "interaction-test",
            title = "Prueba de lectura fluida",
            author = "Luma QA",
            epubPath = epubFile.absolutePath,
            contentPath = File(bookDir, "content").absolutePath,
            chapters = listOf(
                Chapter("Capítulo de prueba", "text/long.xhtml"),
                Chapter("Segundo capítulo", "text/second.xhtml")
            ),
            readerSettings = customSettings,
            totalWords = 2_500
        )
        LibraryRepository(context).save(
            LibrarySnapshot(
                books = listOf(book),
                settings = ReaderSettings(readingMode = ReadingMode.SCROLL)
            )
        )
        return book
    }

    private fun buildLongChapter(paragraphCount: Int): String {
        val paragraphs = (1..paragraphCount).joinToString("\n") { index ->
            "<p>Párrafo $index. La lectura debe mantenerse fluida mientras la persona " +
                "avanza por una historia extensa, sin recargar el capítulo ni bloquear la interfaz.</p>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="es">
              <head><title>Capítulo de prueba</title></head>
              <body><h1>Capítulo de prueba</h1>$paragraphs</body>
            </html>
        """.trimIndent()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluateInReader(script: String): String {
        val result = AtomicReference("")
        val latch = CountDownLatch(1)
        scenario?.onActivity { activity ->
            val webView = findWebView(activity.window.decorView)
                ?: error("No se encontró el lector WebView")
            webView.evaluateJavascript(script) { value ->
                result.set(
                    runCatching { org.json.JSONTokener(value).nextValue() as? String }
                        .getOrNull() ?: value.trim('"')
                )
                latch.countDown()
            }
        }
        assertTrue("El WebView debe responder", latch.await(5, TimeUnit.SECONDS))
        return result.get()
    }
}
