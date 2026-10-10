package io.luma.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.luma.reader.model.AppAccent
import io.luma.reader.model.AppSettings
import io.luma.reader.model.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeneralSettingsSheet(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onOpenReadingSettings: () -> Unit,
    onDismiss: () -> Unit,
    onOpenBackup: () -> Unit = {},
    onOpenDictionary: () -> Unit = {}
) {
    var showCredits by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                SettingsHeader("Ajustes", "Haz que Nova Reader se sienta a tu manera.", onDismiss, "close_settings")
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().testTag("general_settings_sheet"),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsCard("Apariencia", Icons.Default.Palette) {
                        Text("Tema de la aplicación", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                val selected = settings.themeMode == mode
                                Surface(onClick = { onSettingsChanged(settings.copy(themeMode = mode)) },
                                    modifier = Modifier.weight(1f).semantics { this.selected = selected }, shape = RoundedCornerShape(16.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                    Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(when (mode) {
                                            AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                            AppThemeMode.LIGHT -> Icons.Default.LightMode
                                            AppThemeMode.DARK -> Icons.Default.DarkMode
                                        }, null, tint = MaterialTheme.colorScheme.primary)
                                        Text(mode.label, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        Text("Color de acento", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AppAccent.entries) { accent ->
                                FilterChip(selected = settings.accent == accent,
                                    onClick = { onSettingsChanged(settings.copy(accent = accent)) },
                                    leadingIcon = { Box(Modifier.size(14.dp).background(accent.previewColor(), CircleShape)) },
                                    label = { Text(accent.label) })
                            }
                        }
                    }
                }
                item {
                    SettingsCard("Biblioteca", Icons.Default.ViewModule) {
                        Text("Portadas por fila", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(2 to "2 libros", 3 to "3 libros").forEach { (columns, label) ->
                                FilterChip(selected = settings.libraryColumns == columns,
                                    onClick = { onSettingsChanged(settings.copy(libraryColumns = columns)) },
                                    label = { Text(label) })
                            }
                        }
                    }
                }
                item { Text("LECTURA Y TUS DATOS", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { SettingsDestination("Abrir ajustes de lectura", "Página, tipografía, brillo y narración.", Icons.Default.AutoStories, onOpenReadingSettings) }
                item { SettingsDestination("Copias de seguridad y Google Drive", "Protege tus libros, progreso y notas.", Icons.Default.CloudUpload, onOpenBackup) }
                item { SettingsDestination("Abrir diccionario sin conexión", "Consulta palabras sin salir de Nova Reader.", Icons.Default.MenuBook, onOpenDictionary) }
                item {
                    TextButton(onClick = { showCredits = true }) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Diccionario y créditos")
                    }
                    Text("Los cambios se guardan automáticamente. Cada libro puede tener sus propios ajustes de lectura.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
    if (showCredits) AlertDialog(onDismissRequest = { showCredits = false }, title = { Text("Diccionario y créditos") },
        text = { Text("Definiciones de Wikcionario en español, extraídas por Kaikki/Wiktextract y adaptadas a un índice local. Licencia CC BY-SA 4.0. Fuente: https://es.wiktionary.org · https://kaikki.org/eswiktionary/ · Licencia: https://creativecommons.org/licenses/by-sa/4.0/") },
        confirmButton = { TextButton(onClick = { showCredits = false }) { Text("Cerrar") } })
}

private fun AppAccent.previewColor() = when (this) {
    AppAccent.BLUE -> Color(0xFF155EEF)
    AppAccent.VIOLET -> Color(0xFF6558F5)
    AppAccent.OCEAN -> Color(0xFF087E8B)
    AppAccent.FOREST -> Color(0xFF247A55)
}
