package io.luma.reader.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import io.luma.reader.data.LibraryRepository
import io.luma.reader.data.LibraryWrites
import io.luma.reader.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class BackupWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() = runBlocking {
        BackupJobs.cancel(context); BackupJobs.schedule(context, 0, true)
        LibraryWrites.awaitIdle(); context.deleteDatabase("nova-library.db")
        context.getSharedPreferences("luma_library", 0).edit().clear().commit()
        BackupJobs.preferences(context).edit().clear().commit()
        Unit
    }
    @After fun cleanup() = runBlocking {
        BackupJobs.cancel(context); BackupJobs.schedule(context, 0, true)
        LibraryWrites.awaitIdle(); context.deleteDatabase("nova-library.db")
        BackupJobs.preferences(context).edit().clear().commit()
        Unit
    }
    @Test fun workerCreatesAValidLocalBackupAndStoresItsCompletionDate() = runBlocking {
        LibraryRepository(context).save(LibrarySnapshot(vocabulary = listOf(VocabularyWord("word", "cielo", "Atmósfera"))))
        val destination = File(context.cacheDir, "worker-test.zip")
        try {
            val worker = TestListenableWorkerBuilder<BackupWorker>(context)
                .setInputData(workDataOf("destination" to "local", "uri" to Uri.fromFile(destination).toString())).build()
            assertEquals(ListenableWorker.Result.success(workDataOf("message" to "Copia completada")), worker.doWork())
            ZipFile(destination).use { assertNotNull(it.getEntry("manifest.json")) }
            LibraryRepository(context).save(LibrarySnapshot(vocabulary = listOf(VocabularyWord("updated", "mar", "Masa de agua"))))
            val replacement = TestListenableWorkerBuilder<BackupWorker>(context)
                .setInputData(workDataOf("destination" to "local", "uri" to Uri.fromFile(destination).toString())).build()
            assertEquals(ListenableWorker.Result.success(workDataOf("message" to "Copia completada")), replacement.doWork())
            val restored = io.luma.reader.data.BackupRepository(context).restore(destination, LibrarySnapshot())
            assertEquals("mar", restored.vocabulary.single().word)
            assertTrue(BackupJobs.preferences(context).getLong("lastSuccess", 0) > 0)
        } finally { destination.delete() }
    }
    @Test fun scheduledBackupUsesUniqueWorkAndCanBeDisabled() {
        BackupJobs.preferences(context).edit().putString("destination", "local").putString("uri", "content://fixture/backup").commit()
        BackupJobs.schedule(context, 24, true)
        val manager = WorkManager.getInstance(context)
        val first = manager.getWorkInfosForUniqueWork(BackupJobs.PERIODIC).get().filter { !it.state.isFinished }
        assertEquals(1, first.size)
        BackupJobs.schedule(context, 168, true)
        assertEquals(1, manager.getWorkInfosForUniqueWork(BackupJobs.PERIODIC).get().count { !it.state.isFinished })
        BackupJobs.schedule(context, 0, true)
        manager.cancelUniqueWork(BackupJobs.PERIODIC).result.get()
        assertTrue(manager.getWorkInfosForUniqueWork(BackupJobs.PERIODIC).get().all { it.state.isFinished })
    }
}
