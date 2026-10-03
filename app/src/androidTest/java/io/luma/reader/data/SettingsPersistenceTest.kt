package io.luma.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.luma.reader.model.Book
import io.luma.reader.model.Chapter
import io.luma.reader.model.AppSettings
import io.luma.reader.model.LibrarySnapshot
import io.luma.reader.model.ReaderSettings
import io.luma.reader.model.ReaderTheme
import io.luma.reader.model.ReadingMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.filesDir, "library/settings-test")

    @After
    fun cleanUp() {
        context.getSharedPreferences("luma_library", Context.MODE_PRIVATE).edit().clear().commit()
        root.deleteRecursively()
    }

    @Test
    fun globalAndPerBookSettingsRemainIndependent() {
        val content = File(root, "content").apply { mkdirs() }
        val chapter = File(content, "chapter.xhtml").apply {
            writeText("<html><body><p>Prueba</p></body></html>")
        }
        val epub = File(root, "book.epub").apply { writeText("epub") }
        val global = ReaderSettings(
            theme = ReaderTheme.MINT,
            readingMode = ReadingMode.SCROLL,
            ttsLanguageTag = "es-419"
        )
        val custom = global.copy(
            theme = ReaderTheme.OLED,
            readingMode = ReadingMode.PAGED,
            ttsRate = 1.3f,
            ttsPitch = .9f,
            ttsEngine = "com.example.voice",
            ttsLanguageTag = "es-ES",
            ttsVoiceName = "es-es-x-test-local",
            ttsVolume = .72f,
            brightness = .04f,
            extraDim = .55f,
            ttsSentencePauseMs = 240,
            ttsParagraphPauseMs = 680,
            ttsChapterPauseMs = 1_200,
            ttsAutoAdvance = false
        )
        val customBook = Book(
            id = "custom",
            title = "Personalizado",
            author = "QA",
            epubPath = epub.absolutePath,
            contentPath = content.absolutePath,
            chapters = listOf(Chapter("Uno", chapter.name)),
            readerSettings = custom
        )
        val inheritedBook = customBook.copy(id = "inherited", readerSettings = null)
        val repository = LibraryRepository(context)

        repository.save(
            LibrarySnapshot(
                books = listOf(customBook.copy(readingTimeMillis = 93_000L), inheritedBook),
                settings = global,
                appSettings = AppSettings(libraryColumns = 3, includeShelvedBooks = false)
            )
        )
        val restored = repository.load()

        assertEquals(global, restored.settings)
        assertEquals(custom, restored.books.first { it.id == "custom" }.readerSettings)
        assertEquals(93_000L, restored.books.first { it.id == "custom" }.readingTimeMillis)
        assertNull(restored.books.first { it.id == "inherited" }.readerSettings)
        assertEquals(3, restored.appSettings.libraryColumns)
        assertFalse(restored.appSettings.includeShelvedBooks)
    }

    @Test
    fun selectedCoverIsCopiedIntoPrivateBookStorage() {
        val content = File(root, "content").apply { mkdirs() }
        val epub = File(root, "book.epub").apply { writeText("epub") }
        val source = File(context.cacheDir, "luma-custom-cover.png")
        Bitmap.createBitmap(12, 18, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
            source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val book = Book(
            id = "cover-test",
            title = "Portada editable",
            author = "QA",
            epubPath = epub.absolutePath,
            contentPath = content.absolutePath,
            chapters = listOf(Chapter("Uno", "chapter.xhtml"))
        )

        val copied = LibraryRepository(context).copyCustomCover(book, Uri.fromFile(source))

        assertTrue(File(copied).isFile)
        assertTrue(File(copied).canonicalPath.startsWith(root.canonicalPath))
        assertTrue(BitmapFactory.decodeFile(copied) != null)
        source.delete()
    }
}
