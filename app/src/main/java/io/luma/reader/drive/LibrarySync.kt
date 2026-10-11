package io.luma.reader.drive

import android.content.Context
import io.luma.reader.data.BackupRepository
import io.luma.reader.data.LibraryRepository
import io.luma.reader.model.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal data class SyncConflict(val bookId: String, val title: String, val local: Book, val remote: Book)
internal data class SyncPreview(val books: List<Book>, val conflicts: List<SyncConflict>)

/** Progress and annotations only; original files, trash and device settings stay local. */
internal object LibrarySync {
    fun merge(local: LibrarySnapshot, remotes: List<LibrarySnapshot>): SyncPreview {
        val conflicts = mutableListOf<SyncConflict>()
        val result = local.books.map { book ->
            if (book.deletedAt > 0 || book.fingerprint.isBlank()) return@map book
            val remoteBooks = remotes.flatMap { it.books }.filter { it.fingerprint == book.fingerprint && it.deletedAt == 0L }
            val all = listOf(book) + remoteBooks
            val newest = remoteBooks.maxByOrNull { it.modifiedAt }
            val notes = all.flatMap { it.annotations }.groupBy { it.id }.flatMap { (_, versions) ->
                versions.distinctBy { Triple(it.selectedText, it.note, it.colorIndex) }.mapIndexed { index, note ->
                    if (index == 0) note else note.copy(id = stableId(note.id + note.selectedText + note.note + note.colorIndex), note = note.note + "\n[Otra versión sincronizada]")
                }
            }.distinctBy { it.id }
            val marks = all.flatMap { it.bookmarks }.distinctBy { it.id }
            // A remote edit or a deletion never silently discards a saved quote.
            // Deleted versions found on another device return for explicit review.
            val combined = book.copy(annotations = notes, bookmarks = marks,
                annotationTombstones = all.flatMap { it.annotationTombstones }.toSet(),
                bookmarkTombstones = all.flatMap { it.bookmarkTombstones }.toSet())
            if (newest != null && (newest.chapterIndex != book.chapterIndex || kotlin.math.abs(newest.chapterProgress - book.chapterProgress) > .002f || newest.status != book.status))
                conflicts += SyncConflict(book.id, book.title, book, newest)
            combined
        }
        return SyncPreview(result, conflicts)
    }
    private fun stableId(value: String) = UUID.nameUUIDFromBytes(value.toByteArray()).toString()
    suspend fun download(context: Context, token: String, local: LibrarySnapshot): SyncPreview {
        val client = DriveBackupClient(token, kind = "novaSync")
        val ownName = fileName(context)
        val snapshots = mutableListOf<LibrarySnapshot>()
        client.list().filter { it.name != ownName }.forEach { remote ->
            require(remote.size <= 16L * 1024 * 1024) { "Datos de sincronización demasiado grandes" }
            val file = File(context.cacheDir, "sync-${UUID.randomUUID()}.zip")
            try {
                client.download(remote, file) {}
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry("sync.json") ?: error("Sincronización no compatible")
                    val data = java.io.ByteArrayOutputStream()
                    zip.getInputStream(entry).use { BackupRepository.copyLimited(it, data, 16L * 1024 * 1024) }
                    val payload = JSONObject(data.toString("UTF-8"))
                    require(payload.optInt("version") == 1)
                    snapshots += LibraryRepository(context).decodeSnapshot(payload.getJSONObject("library").toString(), checkFiles = false)
                }
            } finally { file.delete() }
        }
        return merge(local, snapshots)
    }
    suspend fun upload(context: Context, token: String, snapshot: LibrarySnapshot) {
        val name = fileName(context)
        val client = DriveBackupClient(token, kind = "novaSync", fileName = name)
        val previous = client.list().firstOrNull { it.name == name }
        val file = File(context.cacheDir, "sync-upload-${UUID.randomUUID()}.zip")
        try {
            val safe = snapshot.copy(books = snapshot.books.filter { it.deletedAt == 0L }.map { it.copy(
                epubPath = "", contentPath = "", coverPath = null, readerSettings = null, review = "",
                series = "", volume = "", tags = emptyList(), collectionId = null
            ) }, collections = emptyList(), vocabulary = emptyList(), readingDays = emptyList())
            val payload = JSONObject().put("version", 1).put("library", JSONObject(LibraryRepository(context).encodeSnapshot(safe))).toString().toByteArray()
            require(payload.size <= 16 * 1024 * 1024) { "La sincronización supera 16 MiB" }
            ZipOutputStream(file.outputStream()).use { it.putNextEntry(ZipEntry("sync.json")); it.write(payload); it.closeEntry() }
            client.upload(file, previous?.id) {}
        } finally { file.delete() }
    }
    private fun fileName(context: Context): String {
        val prefs = context.getSharedPreferences("nova_sync", Context.MODE_PRIVATE)
        val id = prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).commit() }
        return "NovaReader-sync-$id.zip"
    }
    fun applyChoices(current: LibrarySnapshot, preview: SyncPreview, remoteIds: Set<String>): LibrarySnapshot {
        val conflicts = preview.conflicts.associateBy { it.bookId }
        val staged = preview.books.associateBy { it.id }
        return current.copy(books = current.books.map { local ->
            val merged = staged[local.id] ?: return@map local
            val remote = conflicts[local.id]?.remote?.takeIf { local.id in remoteIds }
            val position = remote ?: local
            local.copy(annotations = (local.annotations + merged.annotations).distinctBy { it.id },
                bookmarks = (local.bookmarks + merged.bookmarks).distinctBy { it.id },
                chapterIndex = position.chapterIndex.coerceIn(local.chapters.indices), chapterProgress = position.chapterProgress.coerceIn(0f, 1f),
                progress = local.weightedProgress(position.chapterIndex, position.chapterProgress), readingAnchor = position.readingAnchor,
                status = position.status, modifiedAt = System.currentTimeMillis())
        })
    }
}
