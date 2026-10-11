package io.luma.reader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.Executors

internal class LibraryDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE books (id TEXT PRIMARY KEY, payload TEXT NOT NULL, ordinal INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE metadata (id INTEGER PRIMARY KEY CHECK(id = 1), payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    companion object { const val NAME = "nova-library.db" }
}

/** Ordered durable writes survive Activity destruction without blocking the UI thread. */
internal object LibraryWrites {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nova-library-writer").apply { isDaemon = true }
    }
    fun enqueue(block: () -> Unit) { executor.execute(block) }
    fun awaitIdle() { executor.submit {}.get() }
}

/** Guards book files while a backup snapshot is being packed. */
internal object LibraryFiles { val lock = Any() }
