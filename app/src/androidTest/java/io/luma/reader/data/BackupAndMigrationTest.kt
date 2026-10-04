package io.luma.reader.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.luma.reader.epub.EpubParser
import io.luma.reader.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupAndMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val created = mutableListOf<File>()
    @Before fun reset() { LibraryWrites.awaitIdle(); context.deleteDatabase(LibraryDatabase.NAME); context.getSharedPreferences("luma_library", 0).edit().clear().commit() }
    @After fun cleanup() { LibraryWrites.awaitIdle(); context.deleteDatabase(LibraryDatabase.NAME); context.getSharedPreferences("luma_library", 0).edit().clear().commit(); created.forEach { it.deleteRecursively() } }

    @Test fun legacyMigrationPreservesLibraryNotesSettingsAndBookmarks() {
        val book = fixture().copy(chapterProgress = .63f, readingAnchor = ReadingAnchor(4, 9, "fragmento"),
            bookmarks = listOf(ReaderBookmark("mark-1", 0, .63f)), annotations = listOf(ReaderAnnotation("note-1", 0, note = "No perder")))
        val original = LibrarySnapshot(books = listOf(book), settings = ReaderSettings(theme = ReaderTheme.OLED))
        val codec = LibraryRepository(context)
        context.getSharedPreferences("luma_library", 0).edit().putString("library_state_v1", codec.encodeSnapshot(original)).commit()
        assertEquals(original, LibraryRepository(context).load())
        context.getSharedPreferences("luma_library", 0).edit().clear().commit()
        assertEquals(original, LibraryRepository(context).load())
        val repository = LibraryRepository(context)
        val loaded = repository.load()
        repository.save(loaded.copy(books = loaded.books.map { it.copy(chapterProgress = .84f) }))
        val saved = LibraryRepository(context).load()
        assertEquals(.84f, saved.books.single().chapterProgress)
        assertEquals(book.annotations, saved.books.single().annotations)
        assertEquals(book.bookmarks, saved.books.single().bookmarks)
    }

    @Test fun backupRestoresBooksAfterTheirOriginalDirectoryIsRemoved() {
        val original = fixture().copy(progress = .47f, chapterProgress = .47f, favorite = true, collectionId = "shelf",
            readingAnchor = ReadingAnchor(1, 5, "cielo"), bookmarks = listOf(ReaderBookmark("mark", 0, .47f)),
            annotations = listOf(ReaderAnnotation("note", 0, "cielo", "Comentario", 1, 1, 5, 10)))
        val source = LibrarySnapshot(listOf(original), listOf(BookCollection("shelf", "Novelas")), settings = ReaderSettings(fontSize = 24))
        val archive = BackupRepository(context).create(source).also(created::add)
        File(original.epubPath).parentFile!!.deleteRecursively()
        val existing = fixture().copy(title = "Otro libro")
        val restored = BackupRepository(context).restore(archive, LibrarySnapshot(books = listOf(existing)))
        restored.books.forEach { created += File(it.epubPath).parentFile!! }
        val book = restored.books.first { it.id == original.id }
        assertTrue(File(book.epubPath).isFile)
        assertTrue(File(book.contentPath, book.chapters[0].href).isFile)
        assertNotEquals(original.epubPath, book.epubPath)
        assertEquals(original.annotations, book.annotations)
        assertEquals(original.bookmarks, book.bookmarks)
        assertEquals(original.readingAnchor, book.readingAnchor)
        assertEquals(.47f, book.chapterProgress)
        assertEquals(24, restored.settings.fontSize)
        assertTrue(restored.books.any { it.id == existing.id })
        LibraryRepository(context).save(restored)
        assertEquals(restored, LibraryRepository(context).load())
    }

    @Test fun failedRestoreCleansStagedBooksAndPreservesCurrentLibrary() {
        val good = fixture()
        val badDir = File(context.filesDir, "library/broken-fixture").apply { mkdirs() }.also(created::add)
        val badEpub = File(badDir, "book.epub").apply { writeText("Not an EPUB") }
        val bad = good.copy(id = "broken", epubPath = badEpub.path)
        val archive = BackupRepository(context).create(LibrarySnapshot(books = listOf(good, bad))).also(created::add)
        val directory = File(context.filesDir, "library")
        val before = directory.listFiles()!!.map { it.name }.toSet()
        assertThrows(Exception::class.java) { BackupRepository(context).restore(archive, LibrarySnapshot(books = listOf(good))) }
        assertEquals(before, directory.listFiles()!!.map { it.name }.toSet())
        assertTrue(File(good.epubPath).exists())
    }

    @Test fun backupRejectsPathTraversalBeforeImportingBooks() {
        val book = fixture()
        val source = BackupRepository(context).create(LibrarySnapshot(books = listOf(book))).also(created::add)
        val malicious = File(context.cacheDir, "malicious-backup.zip").also(created::add)
        java.util.zip.ZipFile(source).use { input -> ZipOutputStream(malicious.outputStream()).use { output ->
            input.entries().asSequence().forEach { entry -> output.putNextEntry(ZipEntry(entry.name)); input.getInputStream(entry).use { it.copyTo(output) }; output.closeEntry() }
            output.putNextEntry(ZipEntry("../escape.txt")); output.write("bad".toByteArray()); output.closeEntry()
        } }
        val failure = assertThrows(Exception::class.java) { BackupRepository(context).restore(malicious, LibrarySnapshot(books = listOf(book))) }
        // Android 14+ rejects unsafe ZIP paths in ZipFile before our entry allowlist runs.
        assertTrue(failure is IllegalArgumentException || failure is java.util.zip.ZipException)
        assertTrue(File(book.epubPath).exists())
        assertFalse(File(context.filesDir, "escape.txt").exists())
    }

    @Test fun offlineDictionarySupportsAccentsAndConjugatedWords() = runBlocking {
        val dictionary = OfflineDictionary(context)
        assertTrue(dictionary.lookup("efímero").any { entry -> entry.definitions.any { it.definition.contains("poco tiempo") } })
        assertTrue(dictionary.lookup("efimero").any { it.word == "efímero" })
        assertTrue(dictionary.lookup("hablaba").any { it.word == "hablar" })
        assertTrue(dictionary.suggestions("nove").contains("novela"))
    }

    @Test fun bookSearchFindsTextAcrossChaptersWithoutHtmlTags() = runBlocking {
        val book = fixture()
        val results = io.luma.reader.reader.BookSearch.search(book, "CIELO")
        assertEquals(2, results.size)
        assertEquals(2, results.first().matches)
        assertFalse(results.first().excerpt.contains("<p>"))
    }

    private fun fixture(): Book {
        val file = File(context.cacheDir, "fixture-${java.util.UUID.randomUUID()}.epub").also(created::add)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", "<container><rootfiles><rootfile full-path=\"OEBPS/book.opf\"/></rootfiles></container>")
            entry("OEBPS/book.opf", """<package xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>Novela de prueba</dc:title><dc:creator>QA</dc:creator></metadata><manifest><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>""")
            entry("OEBPS/one.xhtml", "<html><body><h1>Uno</h1><p>El cielo estaba azul. Miró el cielo.</p></body></html>")
            entry("OEBPS/two.xhtml", "<html><body><h1>Dos</h1><p>La luz del cielo.</p></body></html>")
        }
        return EpubParser(context).import(Uri.fromFile(file)).also { created += File(it.epubPath).parentFile!! }
    }
}
