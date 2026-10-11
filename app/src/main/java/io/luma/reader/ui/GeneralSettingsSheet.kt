package io.luma.reader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
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
    onOpenDictionary: () -> Unit = {},
    onManage: (Int) -> Unit = {}
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * .88f
    var showCredits by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxWidth().height(sheetHeight)) {
            Box(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                SettingsHeader("Ajustes", "Tu biblioteca, a tu manera.", onDismiss, "close_settings")
            }
            SettingsSectionTabs(listOf("Estilo", "Biblioteca", "Herramientas"),
                listOf(Icons.Default.Palette, Icons.Default.ViewModule, Icons.Default.Tune),
                section, { section = it }, "settings_tab")
            key(section) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().testTag("general_settings_sheet"),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (section == 0) item {
                        SettingsCard("Apariencia", Icons.Default.Palette) {
                            Text("Tema de la aplicación", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppThemeMode.entries.forEach { mode ->
                                    val selected = settings.themeMode == mode
                                    Surface(onClick = { onSettingsChanged(settings.copy(themeMode = mode)) },
                                        modifier = Modifier.weight(1f).semantics { this.selected = selected }, shape = RoundedCornerShape(16.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ThemePreview(mode, Modifier.fillMaxWidth().height(62.dp))
                                            Text(mode.label, style = MaterialTheme.typography.labelLarge,
                                                modifier = Modifier.padding(bottom = 4.dp))
                                        }
                                    }
                                }
                            }
                            Text("Color de acento", style = MaterialTheme.typography.labelLarge)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppAccent.entries.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { accent ->
                                            val selected = settings.accent == accent
                                            Surface(onClick = { onSettingsChanged(settings.copy(accent = accent)) },
                                                modifier = Modifier.weight(1f).semantics { this.selected = selected },
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Box(Modifier.size(18.dp).background(accent.previewColor(), CircleShape))
                                                    Text(accent.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                                    if (selected) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (section == 1) item {
                        SettingsCard("Tus portadas", Icons.Default.ViewModule) {
                            Text("Portadas por fila", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(2 to "2 libros", 3 to "3 libros").forEach { (columns, label) ->
                                    val selected = settings.libraryColumns == columns
                                    Surface(onClick = { onSettingsChanged(settings.copy(libraryColumns = columns)) },
                                        modifier = Modifier.weight(1f).semantics { this.selected = selected },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                repeat(columns) {
                                                    Box(Modifier.weight(1f).height(32.dp).background(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = if (selected) .5f else .2f), RoundedCornerShape(4.dp)))
                                                }
                                            }
                                            Text(label, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (section == 1) item {
                        SettingsCard("Pantallas y accesibilidad", Icons.Default.AspectRatio) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Adaptar columnas al ancho y al tamaño de letra", Modifier.weight(1f))
                                Switch(checked = settings.adaptiveColumns, onCheckedChange = { onSettingsChanged(settings.copy(adaptiveColumns = it)) })
                            }
                        }
                    }
                    if (section == 1) item { SettingsDestination("Organizar biblioteca", "Selección múltiple, estados y fichas de libros.", Icons.Default.Checklist, { onManage(0) }) }
                    if (section == 1) item { SettingsDestination("Editar estantes", "Nombres y colores de tus colecciones.", Icons.Default.Edit, { onManage(1) }) }
                    if (section == 1) item {
                        SettingsCard("Libros en inicio", Icons.Default.CollectionsBookmark) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Incluir libros en estantes", style = MaterialTheme.typography.titleSmall)
                                    Text("Muestra también los libros que ya organizaste en una colección.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = settings.includeShelvedBooks,
                                    onCheckedChange = { onSettingsChanged(settings.copy(includeShelvedBooks = it)) },
                                    modifier = Modifier.semantics { contentDescription = "Incluir libros en estantes" })
                            }
                        }
                    }
                    if (section == 2) {
                        item { Text("LECTURA Y TUS DATOS", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item { SettingsDestination("Abrir ajustes de lectura", "Página, tipografía, brillo y narración.", Icons.Default.AutoStories, onOpenReadingSettings) }
                        item { SettingsDestination("Copias de seguridad y Google Drive", "Protege tus libros, progreso y notas.", Icons.Default.CloudUpload, onOpenBackup) }
                        item { SettingsDestination("Papelera", "Recupera libros eliminados durante 30 días.", Icons.Default.DeleteOutline, { onManage(2) }) }
                        item { SettingsDestination("Mi vocabulario", "Palabras, definiciones y frases guardadas.", Icons.Default.Bookmark, { onManage(3) }) }
                        item { SettingsDestination("Actividad de lectura", "Calendario, minutos y meta diaria opcional.", Icons.Default.BarChart, { onManage(4) }) }
                        item { SettingsDestination("Abrir diccionario sin conexión", "Consulta palabras sin salir de Nova Reader.", Icons.Default.MenuBook, onOpenDictionary) }
                        item {
                            TextButton(onClick = { showCredits = true }) {
                                Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Diccionario y créditos")
                            }
                            Text("Nova Reader ${io.luma.reader.BuildConfig.VERSION_NAME} · Libros y diccionario sin conexión.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (section != 2) item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Tus cambios se guardan automáticamente.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
    if (showCredits) AlertDialog(onDismissRequest = { showCredits = false }, title = { Text("Diccionario y créditos") },
        text = { Text("Definiciones de Wikcionario en español, extraídas por Kaikki/Wiktextract y adaptadas a un índice local. Licencia CC BY-SA 4.0. Fuente: https://es.wiktionary.org · https://kaikki.org/eswiktionary/ · Licencia: https://creativecommons.org/licenses/by-sa/4.0/\n\nTipografías: Literata, EB Garamond, Libre Baskerville, Lora, Noto Sans, Atkinson Hyperlegible y Roboto Mono. SIL Open Font License 1.1. Estas fuentes se distribuyen con sus licencias completas. Más información: https://openfontlicense.org/") },
        confirmButton = { TextButton(onClick = { showCredits = false }) { Text("Cerrar") } })
}

private fun AppAccent.previewColor() = when (this) {
    AppAccent.BLUE -> Color(0xFF155EEF)
    AppAccent.VIOLET -> Color(0xFF6558F5)
    AppAccent.OCEAN -> Color(0xFF087E8B)
    AppAccent.FOREST -> Color(0xFF247A55)
}

@Composable
private fun ThemePreview(mode: AppThemeMode, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.clip(RoundedCornerShape(9.dp))) {
        val dark = Color(0xFF1A1F2B)
        val light = Color(0xFFF4F6FA)
        drawRect(if (mode == AppThemeMode.DARK) dark else light)
        if (mode == AppThemeMode.SYSTEM) drawRect(dark, topLeft = Offset(size.width / 2f, 0f))
        val inset = 7.dp.toPx()
        drawRoundRect(accent.copy(alpha = .55f), Offset(inset, inset),
            Size(size.width - inset * 2, 5.dp.toPx()), CornerRadius(2.dp.toPx()))
        val gap = 4.dp.toPx()
        val width = (size.width - inset * 2 - gap * 2) / 3f
        repeat(3) { index ->
            drawRoundRect(accent.copy(alpha = .3f + index * .15f),
                Offset(inset + index * (width + gap), inset + 12.dp.toPx()),
                Size(width, size.height - inset * 2 - 12.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
    }
}
