package io.luma.reader.epub

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EpubParserTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun htmlToTextKeepsInlineFragmentsInsideTheSameSentence() {
        val text = EpubParser.htmlToText(
            """
                <html><body>
                  <p>Esta <span>
                    sí es</span> una <em>oración</em> completa.</p>
                  <p>También decodifica&nbsp;entidades.</p>
                </body></html>
            """.trimIndent()
        )

        assertFalse(text.contains("\n sí es"))
        assertEquals(
            "Esta sí es una oración completa. También decodifica entidades.",
            text.replace(Regex("\\s+"), " ")
        )
    }

    @Test
    fun importsEpub3WhenNavigationUsesParentDirectoryReferences() {
        val source = createEpub(
            "epub3-parent-navigation.epub",
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                """.trimIndent(),
                "OPS/package.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>La casa de las mareas</dc:title>
                        <dc:creator>Elena Mar</dc:creator>
                      </metadata>
                      <manifest>
                        <item id="nav" href="navigation/nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="front-image" href="images/front.jpg" media-type="image/jpeg"/>
                        <item id="c1" href="text/chapter-1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c2" href="text/chapter-2.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
                    </package>
                """.trimIndent(),
                "OPS/navigation/nav.xhtml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body><nav epub:type="toc" xmlns:epub="http://www.idpf.org/2007/ops">
                        <ol>
                          <li><a href="../text/chapter-1.xhtml">La llegada</a></li>
                          <li><a href="../text/chapter-2.xhtml">La marea</a></li>
                        </ol>
                      </nav></body>
                    </html>
                """.trimIndent(),
                "OPS/images/front.jpg" to "fake-jpeg-bytes-for-parser-test",
                "OPS/text/chapter-1.xhtml" to chapter(
                    "La llegada",
                    "Una mañana de lluvia comenzó la historia."
                ).replace(
                    "<body>",
                    """<body><div class="full-cover"><img src="../images/front.jpg" alt="Portada"/></div>"""
                ),
                "OPS/text/chapter-2.xhtml" to chapter("La marea", "El mar guardaba una respuesta.")
            )
        )

        val book = EpubParser(context).import(Uri.fromFile(source))

        assertEquals("La casa de las mareas", book.title)
        assertEquals("Elena Mar", book.author)
        assertEquals(listOf("La llegada", "La marea"), book.chapters.map { it.title })
        assertTrue(book.coverPath?.endsWith("OPS/images/front.jpg") == true)
        assertTrue(File(book.contentPath, book.chapters.first().href).exists())
        File(book.epubPath).parentFile?.deleteRecursively()
    }

    @Test
    fun importsEpub2WithNcxTableOfContents() {
        val source = createEpub(
            "epub2-ncx.epub",
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                """.trimIndent(),
                "OEBPS/content.opf" to """
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Horizonte</dc:title><dc:creator>Tomás Río</dc:creator>
                      </metadata>
                      <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="one" href="Text/one.html" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine toc="ncx"><itemref idref="one"/></spine>
                    </package>
                """.trimIndent(),
                "OEBPS/toc.ncx" to """
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                      <navMap><navPoint id="n1"><navLabel><text>Primer horizonte</text></navLabel>
                        <content src="Text/one.html#inicio"/></navPoint></navMap>
                    </ncx>
                """.trimIndent(),
                "OEBPS/Text/one.html" to chapter("Primer horizonte", "La luz apareció detrás de las montañas.")
            ),
            windowsSeparators = true
        )

        val book = EpubParser(context).import(Uri.fromFile(source))

        assertEquals(1, book.chapters.size)
        assertEquals("Primer horizonte", book.chapters.single().title)
        assertTrue(book.totalWords > 0)
        File(book.epubPath).parentFile?.deleteRecursively()
    }

    private fun createEpub(
        name: String,
        entries: Map<String, String>,
        windowsSeparators: Boolean = false
    ): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(if (windowsSeparators) path.replace('/', '\\') else path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }

    private fun chapter(title: String, content: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head><title>$title</title></head>
          <body><h1>$title</h1><p>$content</p></body>
        </html>
    """.trimIndent()
}
