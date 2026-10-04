package io.luma.reader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

internal data class DictionaryDefinition(val partOfSpeech: String, val definition: String)
internal data class DictionaryEntry(val word: String, val definitions: List<DictionaryDefinition>)

internal class OfflineDictionary(private val context: Context) {
    suspend fun lookup(query: String): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        val word = Normalizer.normalize(query.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC).take(80)
        if (word.isBlank()) return@withContext emptyList()
        open().use { db ->
            fun read(column: String, value: String): List<DictionaryEntry> = db.rawQuery(
                "SELECT word,payload FROM entries WHERE $column=? LIMIT 10", arrayOf(value)
            ).use { cursor -> buildList {
                while (cursor.moveToNext()) {
                    val senses = JSONArray(cursor.getString(1))
                    add(DictionaryEntry(cursor.getString(0), (0 until senses.length()).map { index ->
                        senses.getJSONObject(index).let { DictionaryDefinition(it.optString("pos"), it.getString("definition")) }
                    }))
                }
            } }
            read("word", word).ifEmpty {
                val lemma = db.rawQuery("SELECT lemma FROM forms WHERE word=?", arrayOf(word)).use { if (it.moveToFirst()) it.getString(0) else null }
                if (lemma != null) read("word", lemma).map { it.copy(definitions = listOf(DictionaryDefinition("form", "$word: forma de $lemma.")) + it.definitions) }
                else read("folded", fold(word))
            }
        }
    }
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val word = query.trim().lowercase(Locale.ROOT).take(80)
        if (word.length < 2) return@withContext emptyList()
        val pattern = fold(word).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        open().use { db -> db.rawQuery("SELECT word FROM entries WHERE folded LIKE ? ESCAPE '\\' ORDER BY word LIMIT 8", arrayOf(pattern)).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        } }
    }
    private fun open(): SQLiteDatabase = synchronized(lock) {
        val directory = File(context.filesDir, "dictionaries").apply { mkdirs() }
        val file = File(directory, "es-v1.sqlite")
        if (!file.isFile) {
            val temp = File(directory, "es-v1.pending")
            try {
                val parts = context.assets.list("dictionary-es-v1").orEmpty().sorted().iterator()
                check(parts.hasNext()) { "No se encuentran los datos del diccionario" }
                val streams = object : java.util.Enumeration<java.io.InputStream> {
                    override fun hasMoreElements() = parts.hasNext()
                    override fun nextElement() = context.assets.open("dictionary-es-v1/${parts.next()}")
                }
                java.io.SequenceInputStream(streams).use { compressed ->
                    GZIPInputStream(compressed).use { input -> temp.outputStream().use(input::copyTo) }
                }
                check(temp.renameTo(file)) { "No se pudo preparar el diccionario. Comprueba el espacio disponible." }
            } finally { temp.delete() }
        }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }
    companion object {
        private val lock = Any()
        private fun fold(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }
}
