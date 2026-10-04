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
    var accountName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    var authorizing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(accountName) { if (accountName == null) viewModel.clearDriveBackups() }
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
            if (result.hasResolution()) {
                result.pendingIntent?.let { authorizationLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
                    ?: run { authorizing = false; pendingAction = null; viewModel.backupError("No se pudo solicitar el permiso de Drive") }
            } else authorized(result.accessToken)
        }.addOnFailureListener {
            authorizing = false; pendingAction = null
            viewModel.backupError("No se pudo conectar Google Drive. Comprueba la conexión y la cuenta de Google; puedes guardar una copia local mientras tanto.")
        }
    }
    val accountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == Activity.RESULT_OK && !name.isNullOrBlank()) {
            accountName = name
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
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(viewModel::exportBackup) }
    val notesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { it?.let(viewModel::exportNotes) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { localRestore = it }
    val busy = state.busy || authorizing
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().testTag("backup_sheet"), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Copias de seguridad", style = MaterialTheme.typography.headlineMedium)
                Text("Libros, portadas, progreso, estantes, ajustes, marcadores y notas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) item {
                Text(if (authorizing) "Conectando Google Drive…" else state.operation)
                if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.lastResult?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            item {
                Text("En tu dispositivo", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { exportLauncher.launch("NovaReader-copia-${java.time.LocalDate.now()}.zip") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar copia local") }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restaurar copia local") }
                TextButton(onClick = { notesLauncher.launch("NovaReader-notas.md") }, enabled = !busy) { Text("Exportar notas") }
            }
            item {
                HorizontalDivider()
                Text("Google Drive", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                accountName?.let { name ->
                    Text(name, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { accountName = null; viewModel.clearDriveBackups() }, enabled = !busy) { Text("Cambiar cuenta") }
                }
                Text("Opcional. Las copias se guardan en el espacio privado de esta app en tu cuenta. Solo necesitas conexión para guardar o recuperar una copia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { connect("save") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar copia en Drive") }
                OutlinedButton(onClick = { connect("list") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Ver copias de Drive") }
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
                Text("Las copias son manuales. Guardar una copia nueva no borra las anteriores. Si cambias de cuenta, verás las copias de esa cuenta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
    if (localRestore != null || driveRestore != null) {
        AlertDialog(onDismissRequest = { localRestore = null; driveRestore = null },
            title = { Text("¿Recuperar esta copia?") },
            text = { Text("Los libros incluidos recuperarán el progreso, las notas y los ajustes de la copia. Los demás libros de tu biblioteca actual se conservarán.") },
            confirmButton = { TextButton(onClick = {
                localRestore?.let(viewModel::restoreBackup)
                driveRestore?.let { connect("restore:${it.id}") }
                localRestore = null; driveRestore = null
            }) { Text("Recuperar") } },
            dismissButton = { TextButton(onClick = { localRestore = null; driveRestore = null }) { Text("Cancelar") } })
    }
}
