package io.luma.reader.ui

import android.app.Activity
import android.accounts.Account
import android.accounts.AccountManager
import com.google.android.gms.common.AccountPicker
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import io.luma.reader.BackupUiState
import io.luma.reader.MainViewModel
import io.luma.reader.drive.DriveBackup
import io.luma.reader.drive.DriveBackupClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupSheet(state: BackupUiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val activity = LocalContext.current as Activity
    val authorization = remember(activity) { Identity.getAuthorizationClient(activity) }
    val accountName = state.accountName
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    // A recreated sheet must not retain the busy flag of a disposed Google task.
    var authorizing by remember { mutableStateOf(false) }
    var explanationAction by rememberSaveable { mutableStateOf<String?>(null) }
    val attached = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { attached.set(false) } }
    var localRestore by remember { mutableStateOf<Uri?>(null) }
    var driveRestore by remember { mutableStateOf<DriveBackup?>(null) }
    val latestState by rememberUpdatedState(state)
    fun authorized(token: String?) {
        val action = pendingAction
        pendingAction = null; authorizing = false
        if (token.isNullOrBlank()) { viewModel.backupError("No se pudo conectar Google Drive. Puedes seguir usando copias locales."); return }
        when {
            action == "save" -> viewModel.saveToDrive(token)
            action == "list" -> viewModel.listDriveBackups(token)
            action?.startsWith("restore:") == true -> latestState.driveBackups.firstOrNull { it.id == action.substringAfter(':') }
                ?.let { viewModel.restoreFromDrive(token, it) }
        }
    }
    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            pendingAction = null; authorizing = false
            viewModel.backupError("No se conectó Google Drive. Tu biblioteca local sigue disponible.")
        } else try { authorized(authorization.getAuthorizationResultFromIntent(result.data!!).accessToken) }
        catch (e: Exception) { pendingAction = null; authorizing = false; viewModel.backupError("No se pudo conectar Google Drive. Inténtalo de nuevo.") }
    }
    fun requestAuthorization(action: String, name: String) {
        pendingAction = action; authorizing = true; viewModel.clearBackupError()
        val request = AuthorizationRequest.builder().setAccount(Account(name, "com.google"))
            .setRequestedScopes(listOf(Scope(DriveBackupClient.SCOPE))).build()
        authorization.authorize(request).addOnSuccessListener { result ->
            if (!attached.get()) return@addOnSuccessListener
            if (result.hasResolution()) {
                result.pendingIntent?.let { authorizationLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
                    ?: run { authorizing = false; pendingAction = null; viewModel.backupError("No se pudo solicitar el permiso de Drive") }
            } else authorized(result.accessToken)
        }.addOnFailureListener {
            if (!attached.get()) return@addOnFailureListener
            authorizing = false; pendingAction = null
            viewModel.backupError("No se pudo conectar Google Drive. Comprueba la conexión y la cuenta de Google; puedes guardar una copia local mientras tanto.")
        }
    }
    val accountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == Activity.RESULT_OK && !name.isNullOrBlank()) {
            viewModel.selectDriveAccount(name)
            pendingAction?.let { requestAuthorization(it, name) }
        } else {
            pendingAction = null; authorizing = false
            viewModel.backupError("No se eligió una cuenta de Google. Puedes seguir usando copias locales.")
        }
    }
    fun connect(action: String) {
        if (authorizing || state.busy) return
        pendingAction = action
        val name = accountName
        if (name != null) requestAuthorization(action, name)
        else {
            authorizing = true
            try {
                accountLauncher.launch(AccountPicker.newChooseAccountIntent(AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google")).setAlwaysShowAccountPicker(true).build()))
            } catch (e: Exception) {
                pendingAction = null; authorizing = false
                viewModel.backupError("No hay una cuenta o servicios de Google disponibles. Puedes guardar una copia local.")
            }
        }
    }
    fun explainAndConnect(action: String) {
        if (accountName == null) explanationAction = action else connect(action)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(viewModel::exportBackup) }
    val notesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { it?.let(viewModel::exportNotes) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { localRestore = it }
    val busy = state.busy || authorizing
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                SettingsHeader("Copias de seguridad", "Tus libros, progreso y notas, protegidos.", onDismiss, "close_backup")
            }
            LazyColumn(Modifier.fillMaxWidth().testTag("backup_sheet"),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.busy) item { BackupProgress(state) }
                else if (authorizing) item {
                    Text("Conectando Google Drive…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                state.lastResult?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
                item {
                    SettingsCard("En tu dispositivo", Icons.Default.Folder) {
                        Text("Guarda un archivo ZIP donde prefieras. Incluye libros, portadas, estantes, progreso, notas y ajustes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { exportLauncher.launch("NovaReader-copia-${java.time.LocalDate.now()}.zip") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar copia local") }
                        OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restaurar copia local") }
                        TextButton(onClick = { notesLauncher.launch("NovaReader-notas.md") }, enabled = !busy) { Text("Exportar notas") }
                    }
                }
                item {
                    SettingsCard("Google Drive", Icons.Default.CloudUpload) {
                        accountName?.let { name ->
                            Text(name, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { viewModel.selectDriveAccount(null) }, enabled = !busy) { Text("Cambiar cuenta") }
                        }
                        Text("Solo accede al espacio privado de Nova Reader, nunca a tus demás archivos de Drive.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Cada vez que guardas, se actualiza la copia anterior en esta cuenta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { explainAndConnect("save") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar copia en Drive") }
                        OutlinedButton(onClick = { explainAndConnect("list") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Ver copias de Drive") }
                    }
                }
                items(state.driveBackups, key = { it.id }) { file ->
                    OutlinedCard(onClick = { if (!busy) driveRestore = file }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(file.name, style = MaterialTheme.typography.titleSmall)
                            Text("${file.modifiedTime.substringBefore('.').replace('T', ' ')} · ${file.size / 1024 / 1024} MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Recuperar esta copia", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Text("Las copias son manuales. Puedes seguir usando la app mientras guardas. Las copias locales se guardan en el archivo que elijas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
    explanationAction?.let { action ->
        AlertDialog(onDismissRequest = { explanationAction = null },
            icon = { Icon(Icons.Default.Security, null) },
            title = { Text("Tu copia, en tu Drive") },
            text = { Text("Nova Reader solo puede ver, crear y borrar sus propias copias en un espacio privado de tu cuenta. No puede acceder a tus demás archivos de Drive, Gmail ni Fotos.\n\nGoogle te pedirá permiso para sus «datos de configuración»: ese es el espacio donde guardaremos la copia de tus libros, progreso y notas.") },
            confirmButton = { TextButton(onClick = { explanationAction = null; connect(action) }) { Text("Continuar con Google") } },
            dismissButton = { TextButton(onClick = { explanationAction = null }) { Text("Ahora no") } })
    }
    if (localRestore != null || driveRestore != null) {
        AlertDialog(onDismissRequest = { localRestore = null; driveRestore = null },
            title = { Text("¿Recuperar esta copia?") },
            text = { Text("Los libros incluidos recuperarán el progreso, las notas y los ajustes de la copia. Los demás libros de tu biblioteca actual se conservarán.") },
            confirmButton = { TextButton(onClick = {
                localRestore?.let(viewModel::restoreBackup)
                driveRestore?.let { explainAndConnect("restore:${it.id}") }
                localRestore = null; driveRestore = null
            }) { Text("Recuperar") } },
            dismissButton = { TextButton(onClick = { localRestore = null; driveRestore = null }) { Text("Cancelar") } })
    }
}
