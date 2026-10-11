package io.luma.reader.backup

import android.accounts.Account
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import io.luma.reader.data.BackupRepository
import io.luma.reader.data.LibraryRepository
import io.luma.reader.data.LibraryWrites
import io.luma.reader.drive.DriveBackupClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

internal object BackupJobs {
    const val TAG = "nova-durable-backup"
    const val MANUAL = "nova-backup-manual"
    const val PERIODIC = "nova-backup-scheduled"
    private val mutex = Mutex()
    @Volatile var memoryToken: Pair<String, String>? = null
    fun preferences(context: Context) = context.getSharedPreferences("nova_backup_jobs", Context.MODE_PRIVATE)
    fun enqueue(context: Context, destination: String, account: String? = null, uri: String? = null) {
        val data = workDataOf("destination" to destination, "account" to account, "uri" to uri)
        val request = OneTimeWorkRequestBuilder<BackupWorker>().setInputData(data).addTag(TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setConstraints(constraints(destination, false)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, request)
        val prefs = preferences(context)
        prefs.edit().putString("destination", destination).putString("account", account).putString("uri", uri).apply()
        val hours = prefs.getLong("hours", 0)
        if (hours > 0) schedule(context, hours, prefs.getBoolean("wifi", true))
    }
    fun schedule(context: Context, hours: Long, wifiOnly: Boolean) {
        val prefs = preferences(context)
        if (hours == 0L) { WorkManager.getInstance(context).cancelUniqueWork(PERIODIC); prefs.edit().putLong("hours", 0).putBoolean("wifi", wifiOnly).apply(); return }
        val destination = prefs.getString("destination", null) ?: error("Guarda primero una copia para elegir su destino")
        val request = PeriodicWorkRequestBuilder<BackupWorker>(hours, TimeUnit.HOURS)
            .setInputData(workDataOf("destination" to destination, "account" to prefs.getString("account", null), "uri" to prefs.getString("uri", null)))
            .setConstraints(constraints(destination, wifiOnly)).addTag(TAG)
            .setInitialDelay(hours, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        prefs.edit().putLong("hours", hours).putBoolean("wifi", wifiOnly).apply()
    }
    suspend fun cancel(context: Context) = withContext(Dispatchers.IO) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(MANUAL).result.get()
        if (manager.getWorkInfosForUniqueWork(PERIODIC).get().any { it.state == WorkInfo.State.RUNNING }) {
            manager.cancelUniqueWork(PERIODIC).result.get()
            val prefs = preferences(context)
            val hours = prefs.getLong("hours", 0)
            if (hours > 0) schedule(context, hours, prefs.getBoolean("wifi", true))
        }
    }
    private fun constraints(destination: String, wifi: Boolean) = Constraints.Builder().setRequiresStorageNotLow(true)
        .setRequiredNetworkType(if (destination == "drive") { if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED } else NetworkType.NOT_REQUIRED).build()
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
    fun token(context: Context, account: String): String {
        val request = AuthorizationRequest.builder().setAccount(Account(account, "com.google"))
            .setRequestedScopes(listOf(Scope(DriveBackupClient.SCOPE))).build()
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request), 60, TimeUnit.SECONDS)
        check(!result.hasResolution()) { "Abre Copias y vuelve a conectar Google Drive para autorizar esta cuenta" }
        return requireNotNull(result.accessToken) { "Google no devolvió la autorización" }
    }
}

internal class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        BackupJobs.exclusive {
            val prefs = BackupJobs.preferences(applicationContext)
            try {
                setForeground(getForegroundInfo())
                setProgress(workDataOf("label" to "Preparando copia"))
                LibraryWrites.awaitIdle()
                val snapshot = LibraryRepository(applicationContext).load()
                val operationContext = currentCoroutineContext()
                val file = BackupRepository(applicationContext).create(snapshot) { operationContext.ensureActive() }
                try {
                    if (inputData.getString("destination") == "drive") {
                        setProgress(workDataOf("label" to "Guardando en Google Drive"))
                        val account = requireNotNull(inputData.getString("account")) { "Elige una cuenta de Google" }
                        val token = BackupJobs.memoryToken?.takeIf { it.first == account }?.second.also { BackupJobs.memoryToken = null } ?: BackupJobs.token(applicationContext, account)
                        DriveBackupClient(token).saveLatest(file) { value ->
                            setProgressAsync(workDataOf("label" to "Guardando en Google Drive", "progress" to value))
                        }
                    } else {
                        val uri = Uri.parse(requireNotNull(inputData.getString("uri")))
                        applicationContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            file.inputStream().use { BackupRepository.copyLimited(it, output, BackupRepository.MAX_BACKUP) { operationContext.ensureActive() } }
                        } ?: error("No se pudo abrir el destino. Elige de nuevo dónde guardar")
                    }
                } finally { file.delete() }
                val now = System.currentTimeMillis()
                prefs.edit().putLong("lastSuccess", now).putString("lastResult", "Copia completada").remove("lastError").apply()
                Result.success(workDataOf("message" to "Copia completada"))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                val message = e.message ?: "No se pudo completar la copia"
                prefs.edit().putString("lastError", message).apply()
                if (e is java.io.IOException && runAttemptCount < 3) {
                    setProgress(workDataOf("label" to "Reintentando copia cuando haya conexión")); Result.retry()
                } else Result.failure(workDataOf("error" to message))
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("nova-backups", "Copias de seguridad", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "nova-backups")
            .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Nova Reader")
            .setContentText("Guardando copia de seguridad").setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Cancelar", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(210, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(210, notification)
    }
}
