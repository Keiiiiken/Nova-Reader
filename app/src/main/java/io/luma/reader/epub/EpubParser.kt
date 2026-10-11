package io.luma.reader.epub

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Xml
import io.luma.reader.model.Book
import io.luma.reader.model.Chapter
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class EpubParser(private val context: Context, private val extractionLimit: Long = 750L * 1024L * 1024L) {
    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )

    fun import(uri: Uri): Book {
        val id = UUID.randomUUID().toString()
        val bookDir = File(context.filesDir, "library/$id").apply { mkdirs() }
        val epubFile = File(bookDir, "book.epub")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                epubFile.outputStream().use { output -> io.luma.reader.data.BackupRepository.copyLimited(input, output, 512L * 1024 * 1024) }
            } ?: error("No se pudo abrir el archivo.")

            val contentDir = File(bookDir, "content").apply { mkdirs() }
            extractSafely(epubFile, contentDir)

            val container = File(contentDir, "META-INF/container.xml")
            require(container.exists()) { "El EPUB no contiene META-INF/container.xml." }
            val packagePath = readRootFilePath(container)
            val packageFile = safeFile(contentDir, packagePath)
            require(packageFile.exists()) { "No se encontró el paquete principal del EPUB." }

            return parsePackage(id, epubFile, contentDir, packageFile)
        } catch (error: Throwable) {
            bookDir.deleteRecursively()
            throw error
        }
    }

    internal fun detectCover(book: Book): String? = runCatching {
        findLeadingImageCover(File(book.contentPath).canonicalFile, book.chapters)
    }.getOrNull()

    private fun extractSafely(epubFile: File, destination: File) {
        val destinationRoot = destination.canonicalFile
        var totalBytes = 0L
        var entries = 0
        ZipInputStream(FileInputStream(epubFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries++
                require(entries <= 20_000) { "El EPUB contiene demasiados archivos." }
                // ZIP uses forward slashes by specification, but several Windows tools
                // produce EPUB archives with backslashes. Accept both without weakening
                // the canonical-path traversal check below.
                val entryPath = entry.name.replace('\\', '/')
                val output = File(destinationRoot, entryPath).canonicalFile
                require(output.path.startsWith(destinationRoot.path + File.separator)) {
                    "El EPUB contiene una ruta no segura."
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            totalBytes += read
                            require(totalBytes <= extractionLimit) {
                                "El EPUB descomprimido supera el límite de seguridad."
                            }
                            stream.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun readRootFilePath(container: File): String {
        val parser = newParser(container.inputStream())
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", true)) {
                return parser.attr("full-path")
                    ?: error("El contenedor EPUB no declara su paquete.")
            }
            parser.next()
        }
        error("Contenedor EPUB inválido.")
    }

    private fun parsePackage(
        id: String,
        epubFile: File,
        contentDir: File,
        packageFile: File
    ): Book {
        var title = ""
        var author = ""
        var coverId: String? = null
        var tocId: String? = null
        val manifest = linkedMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()
        val parser = newParser(packageFile.inputStream())

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':').lowercase()) {
                    "title" -> if (title.isBlank()) title = parser.nextText().trim()
                    "creator" -> if (author.isBlank()) author = parser.nextText().trim()
                    "meta" -> if (parser.attr("name") == "cover") coverId = parser.attr("content")
                    "manifest" -> Unit
                    "item" -> {
                        val itemId = parser.attr("id").orEmpty()
                        if (itemId.isNotBlank()) {
                            manifest[itemId] = ManifestItem(
                                id = itemId,
                                href = parser.attr("href").orEmpty(),
                                mediaType = parser.attr("media-type").orEmpty(),
                                properties = parser.attr("properties").orEmpty()
                            )
                        }
                    }
                    "spine" -> tocId = parser.attr("toc")
                    "itemref" -> parser.attr("idref")?.let(spine::add)
                }
            }
            parser.next()
        }

        // On Android /data/user/0/... and /data/data/... can point to the same directory.
        // Canonicalize both ends before computing a relative OPF path or the app's absolute
        // data path can accidentally become part of every chapter href.
        val canonicalContentDir = contentDir.canonicalFile
        val opfRelative = packageFile.canonicalFile
            .relativeTo(canonicalContentDir)
            .invariantSeparatorsPath
        val opfBase = opfRelative.substringBeforeLast('/', "")
        fun resolve(href: String): String {
            val clean = href.substringBefore('#')
            val decoded = android.net.Uri.decode(clean)
            return normalizePath(if (opfBase.isBlank()) decoded else "$opfBase/$decoded")
        }

        val navItem = manifest.values.firstOrNull { "nav" in it.properties.split(' ') }
        val ncxItem = tocId?.let(manifest::get)
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        // A malformed table of contents must never make an otherwise readable book fail.
        val chapterTitles = runCatching {
            when {
                navItem != null -> parseNavigation(
                    file = safeFile(contentDir, resolve(navItem.href)),
                    contentRoot = canonicalContentDir
                )
                ncxItem != null -> parseNcx(
                    file = safeFile(contentDir, resolve(ncxItem.href)),
                    contentRoot = canonicalContentDir
                )
                else -> emptyMap()
            }
        }.getOrDefault(emptyMap())

        val orderedItems = spine.mapNotNull { manifest[it] }.ifEmpty {
            // Some hand-made EPUBs omit or damage the spine. Manifest order is a useful,
            // deterministic recovery path and is preferable to rejecting the whole book.
            manifest.values.filter { it.isHtmlDocument() }
        }
        val chapters = orderedItems
            .filter { item ->
                item.isHtmlDocument()
            }
            .mapIndexedNotNull { index, item ->
                val path = resolve(item.href)
                val chapterFile = runCatching { safeFile(canonicalContentDir, path) }.getOrNull()
                if (chapterFile?.exists() != true) return@mapIndexedNotNull null
                Chapter(
                    title = chapterTitles[path]
                        ?: chapterTitles[path.substringBefore('#')]
                        ?: friendlyChapterName(item.href, index),
                    href = path,
                    mediaType = item.mediaType
                )
            }

        require(chapters.isNotEmpty()) {
            "El EPUB no contiene capítulos XHTML/HTML legibles."
        }

        val coverItem = coverId?.let(manifest::get)
            ?: manifest.values.firstOrNull { "cover-image" in it.properties.split(' ') }
        val declaredCoverPath = coverItem?.let {
            runCatching { safeFile(canonicalContentDir, resolve(it.href)) }.getOrNull()
        }
            ?.takeIf(File::exists)
            ?.absolutePath
        val coverPath = declaredCoverPath
            ?: findLeadingImageCover(canonicalContentDir, chapters)
            ?: manifest.values
                .firstOrNull { item ->
                    item.isRasterImage() &&
                        (item.id.contains("cover", true) || item.href.contains("cover", true))
                }
                ?.let { runCatching { safeFile(canonicalContentDir, resolve(it.href)) }.getOrNull() }
                ?.takeIf(File::exists)
                ?.absolutePath

        val weightedChapters = chapters.map { chapter ->
            val file = safeFile(canonicalContentDir, chapter.href)
            val words = if (!file.exists() || file.length() > 4L * 1024L * 1024L) 0
            else htmlToText(readDocument(file)).split(Regex("\\s+")).count { it.isNotBlank() }
            chapter.copy(wordCount = words)
        }
        val totalWords = weightedChapters.sumOf { it.wordCount }

        return Book(
            id = id,
            title = title.ifBlank { "Libro sin título" },
            author = author.ifBlank { "Autor desconocido" },
            epubPath = epubFile.absolutePath,
            contentPath = canonicalContentDir.absolutePath,
            coverPath = coverPath,
            chapters = weightedChapters,
            totalWords = totalWords
        )
    }

    private fun ManifestItem.isHtmlDocument(): Boolean {
        val type = mediaType.lowercase()
        val path = href.substringBefore('#').substringBefore('?')
        return type.contains("html") ||
            path.endsWith(".xhtml", true) ||
            path.endsWith(".html", true) ||
            path.endsWith(".htm", true)
    }

    private fun ManifestItem.isRasterImage(): Boolean {
        val type = mediaType.lowercase()
        val extension = href.substringBefore('#').substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()
        return type in RASTER_MEDIA_TYPES || extension in RASTER_EXTENSIONS
    }

    /**
     * EPUB 2 books frequently omit the official cover metadata and instead put a
     * full-page image in their first spine document. Treat that leading image as
     * the cover only when it appears before substantial body text.
     */
    private fun findLeadingImageCover(contentRoot: File, chapters: List<Chapter>): String? {
        chapters.take(2).forEach { chapter ->
            val document = runCatching {
                readDocument(safeFile(contentRoot, chapter.href))
            }.getOrNull() ?: return@forEach
            val bodyStart = BODY_TAG.find(document)?.range?.last?.plus(1) ?: 0
            val imageMatch = IMAGE_TAG.find(document, bodyStart) ?: return@forEach
            if (imageMatch.range.first - bodyStart > MAX_LEADING_IMAGE_OFFSET) return@forEach

            val textBeforeImage = htmlToText(
                document.substring(bodyStart, imageMatch.range.first)
            )
            if (textBeforeImage.length > MAX_TEXT_BEFORE_COVER) return@forEach

            val reference = (1..3)
                .firstNotNullOfOrNull { group -> imageMatch.groups[group]?.value }
                ?.replace("&amp;", "&")
                ?.substringBefore('#')
                ?.substringBefore('?')
                ?.trim()
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.startsWith("data:", true) &&
                        !it.startsWith("http:", true) &&
                        !it.startsWith("https:", true)
                }
                ?: return@forEach

            val chapterFile = safeFile(contentRoot, chapter.href)
            val imageFile = runCatching {
                resolveReference(contentRoot, chapterFile, reference)
            }.getOrNull() ?: return@forEach
            if (
                imageFile.exists() &&
                imageFile.isFile &&
                imageFile.length() > 0L &&
                imageFile.extension.lowercase() in RASTER_EXTENSIONS
            ) {
                return imageFile.absolutePath
            }
        }
        return null
    }

    private fun parseNavigation(file: File, contentRoot: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val result = linkedMapOf<String, String>()
        val parser = newParser(file.inputStream())
        var href: String? = null
        var depth = 0
        var text = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.substringAfter(':').equals("a", true)) {
                        href = parser.attr("href")
                        depth = parser.depth
                        text = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> if (href != null && parser.depth >= depth) text.append(parser.text)
                XmlPullParser.END_TAG -> if (
                    href != null &&
                    parser.depth == depth &&
                    parser.name.substringAfter(':').equals("a", true)
                ) {
                    val absolute = resolveReference(contentRoot, file, href!!.substringBefore('#'))
                    val path = absolute.relativeTo(contentRoot.canonicalFile).invariantSeparatorsPath
                    result[normalizePath(path)] = text.toString().trim()
                    href = null
                }
            }
            parser.next()
        }
        return result.filterValues { it.isNotBlank() }
    }

    private fun parseNcx(file: File, contentRoot: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val result = linkedMapOf<String, String>()
        val parser = newParser(file.inputStream())
        var label = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':').lowercase()) {
                    "text" -> label = parser.nextText().trim()
                    "content" -> parser.attr("src")?.let { src ->
                        val target = resolveReference(contentRoot, file, src.substringBefore('#'))
                        result[
                            normalizePath(
                                target.relativeTo(contentRoot.canonicalFile).invariantSeparatorsPath
                            )
                        ] = label
                    }
                }
            }
            parser.next()
        }
        return result.filterValues { it.isNotBlank() }
    }

    private fun resolveReference(contentRoot: File, sourceFile: File, reference: String): File {
        val root = contentRoot.canonicalFile
        val decoded = android.net.Uri.decode(reference)
        val target = File(sourceFile.parentFile, decoded).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
            "El índice EPUB contiene una ruta no segura."
        }
        return target
    }

    private fun newParser(input: InputStream): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, null)
            next()
        }

    private fun XmlPullParser.attr(name: String): String? {
        for (index in 0 until attributeCount) {
            if (getAttributeName(index).substringAfter(':').equals(name, true)) {
                return getAttributeValue(index)
            }
        }
        return null
    }

    private fun safeFile(root: File, relative: String): File {
        val rootFile = root.canonicalFile
        val target = File(rootFile, relative).canonicalFile
        require(target.path == rootFile.path || target.path.startsWith(rootFile.path + File.separator)) {
            "Ruta EPUB no segura."
        }
        return target
    }

    private fun normalizePath(path: String): String {
        val parts = mutableListOf<String>()
        path.replace('\\', '/').split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += it
            }
        }
        return parts.joinToString("/")
    }

    private fun friendlyChapterName(href: String, index: Int): String {
        val raw = href.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("[_-]+"), " ")
            .trim()
        return raw.takeIf { it.length > 2 && !it.all(Char::isDigit) }
            ?.replaceFirstChar(Char::uppercase)
            ?: "Capítulo ${index + 1}"
    }

    companion object {
        private val RASTER_MEDIA_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif"
        )
        private val RASTER_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "avif"
        )
        private val BODY_TAG = Regex("(?is)<body\\b[^>]*>")
        private val IMAGE_TAG = Regex(
            """(?is)<(?:img|image)\b[^>]*?(?:src|href|xlink:href)\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s>]+))"""
        )
        private const val MAX_LEADING_IMAGE_OFFSET = 6_000
        private const val MAX_TEXT_BEFORE_COVER = 240

        fun readDocument(file: File): String {
            val bytes = file.readBytes()
            return when {
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                    bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                    bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
                else -> bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            }
        }

        fun htmlToText(html: String): String {
            val narrativeHtml = html.replace(
                Regex("(?is)<(script|style|noscript).*?>.*?</\\1>"),
                " "
            )
            return Html.fromHtml(narrativeHtml, Html.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace('\uFFFC', ' ')
                // Source-code line breaks inside inline spans are not narrative
                // pauses. Android's HTML parser already preserves real blocks.
                .replace(Regex("[\\t\\r ]+"), " ")
                .replace(Regex(" *\\n *"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        }
    }
}
