package io.luma.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.testTag("general_settings_sheet"),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("AJUSTES DE LA APP", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
                        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text("Apariencia general y preferencias de la aplicación", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SettingsGroupTitle("Tema de la aplicación", Icons.Default.DarkMode)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 22.dp)
                ) {
                    items(AppThemeMode.entries) { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSettingsChanged(settings.copy(themeMode = mode)) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
            item {
                SettingsGroupTitle("Color de acento", Icons.Default.Palette)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 22.dp)
                ) {
                    items(AppAccent.entries) { accent ->
                        FilterChip(
                            selected = settings.accent == accent,
                            onClick = { onSettingsChanged(settings.copy(accent = accent)) },
                            label = { Text(accent.label) }
                        )
                    }
                }
            }
            item {
                SettingsGroupTitle("Tamaño de la biblioteca", Icons.Default.ViewModule)
                Text(
                    "Elige cuántas portadas aparecen por línea en Biblioteca y dentro de cada estante.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    listOf(2 to "2 libros", 3 to "3 libros").forEach { (columns, label) ->
                        FilterChip(
                            selected = settings.libraryColumns == columns,
                            onClick = { onSettingsChanged(settings.copy(libraryColumns = columns)) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        SettingsGroupTitle("Lectura", Icons.Default.Tune)
                        Text(
                            "La tipografía, temas de página, tamaño, desplazamiento y voz se guardan en los ajustes de lectura.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(12.dp))
                        OutlinedButton(onClick = onOpenReadingSettings, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Tune, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Abrir ajustes de lectura")
                        }
                    }
                }
            }
            item {
                Text(
                    "Los cambios de esta pantalla afectan a toda la aplicación. Los ajustes de lectura generales pueden ser sustituidos por una novela desde su propio lector.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
