package io.luma.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.luma.reader.drive.LibrarySync
import io.luma.reader.importer.BookImporter
import io.luma.reader.importer.DuplicateBookException
import io.luma.reader.model.*
import io.luma.reader.reader.InternalLinks
import io.luma.reader.ui.renderBookPage
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ExpandedLibraryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val created = mutableListOf<File>()
    @After fun cleanup() { created.forEach { it.deleteRecursively() } }
    private fun track(book: Book) = book.also { created += File(it.epubPath).parentFile!! }
    private fun epub(): File {
        val file = File.createTempFile("new-features", ".epub", context.cacheDir).also(created::add)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            put("META-INF/container.xml", "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>")
            put("book.opf", "<package><metadata><title>Dos capítulos</title><creator>Autora</creator></metadata><manifest><item id='a' href='a.xhtml' media-type='application/xhtml+xml'/><item id='b' href='b.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>")
            put("a.xhtml", "<html><body><p>Uno dos</p><a href='b.xhtml#nota'>Nota</a></body></html>")
            put("b.xhtml", "<html><body><p id='nota'>Esta referencia explica algo sin salir del punto de lectura.</p></body></html>")
        }
        return file
    }
    @Test fun repeatedImportIsRejectedByContentEvenWithDifferentName() {
        val file = epub(); val importer = BookImporter(context)
        val book = track(importer.import(Uri.fromFile(file)))
        val renamed = File(context.cacheDir, "renamed.epub").also { file.copyTo(it, true); created += it }
        val before = File(context.filesDir, "library").listFiles()!!.size
        assertThrows(DuplicateBookException::class.java) { importer.import(Uri.fromFile(renamed), setOf(book.fingerprint)) }
        assertEquals(before, File(context.filesDir, "library").listFiles()!!.size)
    }
    @Test fun extendedMetadataSurvivesCodecAndArchive() {
        val book = track(BookImporter(context).import(Uri.fromFile(epub()))).copy(status = ReadingStatus.ABANDONED,
            series = "Saga", volume = "2", tags = listOf("Fantasía", "Favoritos"), deletedAt = 100L, modifiedAt = 200L,
            annotations = listOf(ReaderAnnotation("note", 0, note = "Conservar")), annotationTombstones = setOf("old"))
        val state = LibrarySnapshot(books = listOf(book), vocabulary = listOf(VocabularyWord("word", "efímero", "De corta duración", "Todo es efímero", book.title)),
            readingDays = listOf(ReadingDay("2026-10-11", 180000)), appSettings = AppSettings(dailyGoalMinutes = 20, adaptiveColumns = false))
        val codec = LibraryRepository(context)
        assertEquals(state, codec.decodeSnapshot(codec.encodeSnapshot(state)))
        val archive = BackupRepository(context).create(state).also(created::add)
        val restored = BackupRepository(context).restore(archive, LibrarySnapshot())
        restored.books.forEach(::track)
        assertEquals(book.status, restored.books.single().status)
        assertEquals(book.deletedAt, restored.books.single().deletedAt)
        assertEquals(book.annotations, restored.books.single().annotations)
        assertEquals(state.vocabulary, restored.vocabulary)
        assertEquals(state.readingDays, restored.readingDays)
        assertEquals(state.appSettings, restored.appSettings)
    }
    @Test fun progressWeightsChaptersByTheirTextAndSupportsManualStatus() {
        val book = track(BookImporter(context).import(Uri.fromFile(epub())))
        assertTrue(book.chapters[1].wordCount > book.chapters[0].wordCount)
        assertTrue(book.weightedProgress(0, 1f) < .5f)
        assertEquals(1f, book.weightedProgress(1, 1f))
        assertEquals(ReadingStatus.FINISHED, book.copy(status = ReadingStatus.FINISHED, progress = .1f).effectiveStatus)
    }
    @Test fun footnotesResolveLocallyAndRejectFilesOutsideTheBook() {
        val book = track(BookImporter(context).import(Uri.fromFile(epub())))
        val uri = Uri.fromFile(File(book.contentPath, "b.xhtml")).buildUpon().fragment("nota").build()
        val link = InternalLinks.resolve(book, uri)
        assertTrue(link.text.contains("explica algo")); assertEquals(1, link.chapterIndex)
        assertThrows(IllegalArgumentException::class.java) { InternalLinks.resolve(book, Uri.fromFile(epub())) }
    }
    @Test fun syncPreservesBothNoteEditsAndRequiresAnExplicitProgressChoice() {
        val original = track(BookImporter(context).import(Uri.fromFile(epub())))
        val local = original.copy(chapterIndex = 0, chapterProgress = .4f, annotations = listOf(ReaderAnnotation("same", 0, note = "Local")))
        val remote = original.copy(id = "other-device", chapterIndex = 1, chapterProgress = .8f, modifiedAt = 10,
            annotations = listOf(ReaderAnnotation("same", 0, note = "Remota")), bookmarks = listOf(ReaderBookmark("mark", 1, .8f)))
        val current = LibrarySnapshot(books = listOf(local))
        val preview = LibrarySync.merge(current, listOf(LibrarySnapshot(books = listOf(remote))))
        assertEquals(1, preview.conflicts.size)
        val kept = LibrarySync.applyChoices(current, preview, emptySet()).books.single()
        assertEquals(0, kept.chapterIndex); assertEquals(2, kept.annotations.size); assertEquals(1, kept.bookmarks.size)
        val selected = LibrarySync.applyChoices(current, preview, setOf(local.id)).books.single()
        assertEquals(1, selected.chapterIndex); assertEquals(.8f, selected.chapterProgress)
        assertEquals(local.epubPath, selected.epubPath)
        val repeated = LibrarySync.merge(LibrarySnapshot(books = listOf(selected)), listOf(LibrarySnapshot(books = listOf(remote))))
        assertEquals(2, repeated.books.single().annotations.size)
    }
    @Test fun pdfAndCbzImportRenderAndSurviveBackup() {
        val pdf = File.createTempFile("document", ".pdf", context.cacheDir).also(created::add)
        val document = PdfDocument()
        try {
            repeat(2) { index -> val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                page.canvas.drawColor(if (index == 0) android.graphics.Color.WHITE else android.graphics.Color.YELLOW); document.finishPage(page) }
            pdf.outputStream().use(document::writeTo)
        } finally { document.close() }
        val pdfBook = track(BookImporter(context).import(Uri.fromFile(pdf)))
        assertEquals(BookFormat.PDF, pdfBook.format); assertEquals(2, pdfBook.chapters.size)
        renderBookPage(pdfBook, 1).also { assertTrue(it.width > 0); it.recycle() }
        val cbz = File.createTempFile("comic", ".cbz", context.cacheDir).also(created::add)
        val image = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        ZipOutputStream(cbz.outputStream()).use { zip ->
            for (i in listOf(10, 2, 1)) { zip.putNextEntry(ZipEntry("page-$i.png")); image.eraseColor(i); image.compress(Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry() }
        }; image.recycle()
        val comic = track(BookImporter(context).import(Uri.fromFile(cbz)))
        assertEquals(BookFormat.CBZ, comic.format); assertEquals(3, comic.chapters.size)
        renderBookPage(comic, 0).also { assertEquals(20, it.width); it.recycle() }
        val archive = BackupRepository(context).create(LibrarySnapshot(books = listOf(pdfBook, comic))).also(created::add)
        val restored = BackupRepository(context).restore(archive, LibrarySnapshot())
        restored.books.forEach(::track)
        assertEquals(listOf(BookFormat.PDF, BookFormat.CBZ), restored.books.map { it.format })
        restored.books.forEach { renderBookPage(it, 0).recycle() }
    }
}
