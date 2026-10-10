package io.luma.reader.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.luma.reader.model.ReaderAlignment
import io.luma.reader.model.ReaderFont
import io.luma.reader.model.ReaderSettings
import io.luma.reader.model.ReaderTheme
import io.luma.reader.model.ReadingMode
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsTab(val label: String) {
    APPEARANCE("Apariencia"),
    READING("Lectura"),
    VOICE("Voz")
}

private data class Choice(val id: String?, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    compactPreview: Boolean,
    isBookOverride: Boolean = false,
    onUseGlobalSettings: (() -> Unit)? = null,
    onBrightnessChanged: ((Float) -> Unit)? = null,
    onExtraDimChanged: ((Float) -> Unit)? = null
) {
    var tab by remember { mutableStateOf(SettingsTab.APPEARANCE) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // The content determines the sheet height so Material can keep it anchored
    // to the bottom. Constraining ModalBottomSheet itself places it at the top.
    val contentHeight = screenHeight * if (compactPreview) .50f else .86f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = if (compactPreview) Color.Black.copy(alpha = .12f)
        else Color.Black.copy(alpha = .32f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .testTag(
                    if (compactPreview) "novel_settings_sheet"
                    else "general_settings_sheet"
                )
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                SettingsHeader(
                    title = if (compactPreview) "Este libro" else "Tu forma de leer",
                    subtitle = when {
                        compactPreview && isBookOverride -> "Personalizados solo para este libro."
                        compactPreview -> "Tus cambios se aplicarán solo a este libro."
                        else -> "Preferencias para los libros sin ajustes propios."
                    },
                    onClose = onDismiss,
                    closeTag = "close_reader_settings"
                )
                if (compactPreview && isBookOverride && onUseGlobalSettings != null) {
                    TextButton(onClick = onUseGlobalSettings) { Text("Usar generales") }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SettingsTab.entries.forEach { option ->
                        Surface(
                            onClick = { tab = option },
                            shape = RoundedCornerShape(17.dp),
                            color = if (tab == option) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (tab == option) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 11.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            when (tab) {
                SettingsTab.APPEARANCE -> AppearanceSettings(settings, onSettingsChanged)
                SettingsTab.READING -> ReadingBehaviorSettings(
                    settings,
                    onSettingsChanged,
                    onBrightnessChanged,
                    onExtraDimChanged
                )
                SettingsTab.VOICE -> VoiceSettings(settings, onSettingsChanged)
            }
        }
    }
}

@Composable
private fun AppearanceSettings(
    settings: ReaderSettings,
    onChanged: (ReaderSettings) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SettingLabel("Tema")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 22.dp)
            ) {
                items(ReaderTheme.entries) { theme ->
                    ThemeOption(
                        theme = theme,
                        selected = settings.theme == theme,
                        onClick = { onChanged(settings.copy(theme = theme)) }
                    )
                }
            }
        }
        item {
            SettingLabel("Tipografía")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 22.dp)
            ) {
                items(ReaderFont.entries) { font ->
                    FilterChip(
                        selected = settings.font == font,
                        onClick = { onChanged(settings.copy(font = font)) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Aa",
                                    fontFamily = font.previewFamily(),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(font.label)
                            }
                        }
                    )
                }
            }
        }
        item {
            SettingLabel("Alineación")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderAlignment.entries.forEach { alignment ->
                    FilterChip(
                        selected = settings.alignment == alignment,
                        onClick = { onChanged(settings.copy(alignment = alignment)) },
                        label = { Text(alignment.label) }
                    )
                }
            }
        }
        item {
            SettingSlider(
                label = "Tamaño",
                valueLabel = "${settings.fontSize}",
                value = settings.fontSize.toFloat(),
                range = 12f..34f,
                steps = 21
            ) { onChanged(settings.copy(fontSize = it.roundToInt())) }
        }
        item {
            SettingSlider(
                label = "Interlineado",
                valueLabel = String.format(Locale.getDefault(), "%.1f", settings.lineHeight),
                value = settings.lineHeight,
                range = 1.2f..2.2f,
                steps = 9
            ) { onChanged(settings.copy(lineHeight = it)) }
        }
    }
}

@Composable
private fun ReadingBehaviorSettings(
    settings: ReaderSettings,
    onChanged: (ReaderSettings) -> Unit,
    onBrightnessChanged: ((Float) -> Unit)?,
    onExtraDimChanged: ((Float) -> Unit)?
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SettingLabel("Modo de lectura")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadingModeCard(
                    title = "Desplazamiento",
                    description = "Lectura vertical continua",
                    selected = settings.readingMode == ReadingMode.SCROLL,
                    icon = { Icon(Icons.Default.SwapVert, null) },
                    modifier = Modifier.weight(1f)
                ) { onChanged(settings.copy(readingMode = ReadingMode.SCROLL)) }
                ReadingModeCard(
                    title = "Paginado",
                    description = "Desliza de lado por páginas",
                    selected = settings.readingMode == ReadingMode.PAGED,
                    icon = { Icon(Icons.Default.ViewCarousel, null) },
                    modifier = Modifier.weight(1f)
                ) { onChanged(settings.copy(readingMode = ReadingMode.PAGED)) }
            }
        }
        item {
            SettingSlider(
                label = "Márgenes",
                valueLabel = "${settings.horizontalMargin}",
                value = settings.horizontalMargin.toFloat(),
                range = 8f..48f,
                steps = 19
            ) { onChanged(settings.copy(horizontalMargin = it.roundToInt())) }
        }
        item {
            SettingSlider(
                label = "Brillo de pantalla",
                valueLabel = "${(settings.brightness * 100).roundToInt()}%",
                value = settings.brightness,
                range = .02f..1f,
                steps = 48
            ) { value ->
                if (onBrightnessChanged != null) onBrightnessChanged(value)
                else onChanged(settings.copy(brightness = value))
            }
        }
        item {
            SettingSlider(
                label = "Oscurecimiento extra",
                valueLabel = "${(settings.extraDim * 100).roundToInt()}%",
                value = settings.extraDim,
                range = 0f..0.8f,
                steps = 15
            ) { value ->
                if (onExtraDimChanged != null) onExtraDimChanged(value)
                else onChanged(settings.copy(extraDim = value))
            }
            Text(
                "Añade una capa oscura para leer de noche incluso cuando el brillo mínimo del teléfono sigue siendo alto.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mantener pantalla activa", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Evita que se apague mientras lees.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { onChanged(settings.copy(keepScreenOn = it)) }
                )
            }
        }
    }
}

@Composable
private fun VoiceSettings(
    settings: ReaderSettings,
    onChanged: (ReaderSettings) -> Unit
) {
    val context = LocalContext.current
    val engines = remember {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val installed = context.packageManager.queryIntentServices(intent, 0)
            .mapNotNull { info ->
                val packageName = info.serviceInfo?.packageName ?: return@mapNotNull null
                Choice(packageName, info.loadLabel(context.packageManager).toString())
            }
            .distinctBy { it.id }
        listOf(Choice(null, "Motor predeterminado del sistema")) + installed
    }
    val languages = remember {
        listOf(
            Choice("system", "Idioma del sistema"),
            Choice("es-419", "Español — Latinoamérica"),
            Choice("es-ES", "Español — España"),
            Choice("en-US", "English — United States"),
            Choice("en-GB", "English — United Kingdom"),
            Choice("pt-BR", "Português — Brasil"),
            Choice("fr-FR", "Français — France"),
            Choice("de-DE", "Deutsch — Deutschland"),
            Choice("it-IT", "Italiano — Italia")
        )
    }
    var previewEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var previewReady by remember { mutableStateOf(false) }
    var availableVoices by remember { mutableStateOf(emptyList<Voice>()) }

    val requestedLocale = if (settings.ttsLanguageTag == "system") Locale.getDefault()
    else Locale.forLanguageTag(settings.ttsLanguageTag)
    val matchingVoices = remember(availableVoices, settings.ttsLanguageTag) {
        val sameLanguage = availableVoices.filter { it.locale.language == requestedLocale.language }
        (sameLanguage.ifEmpty { availableVoices })
            .sortedWith(
                compareBy<Voice> { it.locale.country != requestedLocale.country }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name }
            )
            .take(32)
    }
    val voiceChoices = listOf(Choice(null, "Automática para el idioma")) +
        matchingVoices.mapIndexed { index, voice ->
            Choice(voice.name, voiceChoiceLabel(voice, index))
        }

    DisposableEffect(settings.ttsEngine) {
        previewReady = false
        availableVoices = emptyList()
        val listener = TextToSpeech.OnInitListener { status ->
            previewReady = status == TextToSpeech.SUCCESS
            if (status == TextToSpeech.SUCCESS) {
                Handler(Looper.getMainLooper()).post {
                    availableVoices = previewEngine?.voices
                        ?.filter { it.locale.language.isNotBlank() }
                        ?.toList()
                        .orEmpty()
                }
            }
        }
        val engine = if (settings.ttsEngine == null) {
            TextToSpeech(context, listener)
        } else {
            TextToSpeech(context, listener, settings.ttsEngine)
        }
        previewEngine = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            previewEngine = null
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            DropdownSetting(
                label = "Motor de voz",
                icon = { Icon(Icons.Default.SettingsVoice, null) },
                selected = engines.firstOrNull { it.id == settings.ttsEngine }?.label
                    ?: "Motor predeterminado del sistema",
                choices = engines
            ) { onChanged(settings.copy(ttsEngine = it.id, ttsVoiceName = null)) }
        }
        item {
            DropdownSetting(
                label = "Idioma",
                icon = { Icon(Icons.Default.Language, null) },
                selected = languages.firstOrNull { it.id == settings.ttsLanguageTag }?.label
                    ?: "Idioma del sistema",
                choices = languages
            ) {
                onChanged(
                    settings.copy(
                        ttsLanguageTag = it.id ?: "system",
                        ttsVoiceName = null
                    )
                )
            }
        }
        item {
            DropdownSetting(
                label = "Voz concreta",
                icon = { Icon(Icons.Default.RecordVoiceOver, null) },
                selected = voiceChoices.firstOrNull { it.id == settings.ttsVoiceName }?.label
                    ?: settings.ttsVoiceName
                    ?: "Automática para el idioma",
                choices = voiceChoices
            ) { onChanged(settings.copy(ttsVoiceName = it.id)) }
        }
        item {
            SettingSlider(
                label = "Velocidad",
                valueLabel = "${String.format(Locale.getDefault(), "%.1f", settings.ttsRate)}×",
                value = settings.ttsRate,
                range = .5f..2f,
                steps = 14
            ) { onChanged(settings.copy(ttsRate = it)) }
        }
        item {
            SettingSlider(
                label = "Tono",
                valueLabel = "${String.format(Locale.getDefault(), "%.1f", settings.ttsPitch)}×",
                value = settings.ttsPitch,
                range = .5f..2f,
                steps = 14
            ) { onChanged(settings.copy(ttsPitch = it)) }
        }
        item {
            SettingSlider(
                label = "Volumen de narración",
                valueLabel = "${(settings.ttsVolume * 100).roundToInt()}%",
                value = settings.ttsVolume,
                range = .1f..1f,
                steps = 8
            ) { onChanged(settings.copy(ttsVolume = it)) }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Continuar entre capítulos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Salta capítulos sin texto y sigue narrando sin tener que pulsar reproducir.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = settings.ttsAutoAdvance,
                    onCheckedChange = { onChanged(settings.copy(ttsAutoAdvance = it)) }
                )
            }
        }
        item {
            SettingSlider(
                label = "Pausa entre oraciones",
                valueLabel = "${settings.ttsSentencePauseMs} ms",
                value = settings.ttsSentencePauseMs.toFloat(),
                range = 0f..1_500f,
                steps = 29
            ) { onChanged(settings.copy(ttsSentencePauseMs = it.roundToInt())) }
        }
        item {
            SettingSlider(
                label = "Pausa entre párrafos",
                valueLabel = "${settings.ttsParagraphPauseMs} ms",
                value = settings.ttsParagraphPauseMs.toFloat(),
                range = 0f..3_000f,
                steps = 29
            ) { onChanged(settings.copy(ttsParagraphPauseMs = it.roundToInt())) }
        }
        item {
            SettingSlider(
                label = "Pausa entre capítulos",
                valueLabel = "${settings.ttsChapterPauseMs} ms",
                value = settings.ttsChapterPauseMs.toFloat(),
                range = 0f..5_000f,
                steps = 24
            ) { onChanged(settings.copy(ttsChapterPauseMs = it.roundToInt())) }
        }
        item {
            Button(
                onClick = {
                    previewEngine?.apply {
                        val selectedVoice = settings.ttsVoiceName?.let { voiceName ->
                            voices?.firstOrNull { it.name == voiceName }
                        }
                        if (selectedVoice != null) voice = selectedVoice
                        else language = requestedLocale
                        setSpeechRate(settings.ttsRate)
                        setPitch(settings.ttsPitch)
                        val parameters = Bundle().apply {
                            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.ttsVolume)
                        }
                        speak(
                            "Así sonará la narración en Luma Reader.",
                            TextToSpeech.QUEUE_FLUSH,
                            parameters,
                            "luma_voice_preview"
                        )
                    }
                },
                enabled = previewReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Probar esta voz")
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.RecordVoiceOver, null)
                Spacer(Modifier.width(8.dp))
                Text("Descargar o gestionar voces")
            }
        }
        item {
            Text(
                "Android no identifica oficialmente el género de cada voz. El selector muestra todas las voces que entrega tu motor; puedes probarlas y elegir una masculina si está instalada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun voiceChoiceLabel(voice: Voice, index: Int): String {
    val localeName = voice.locale.getDisplayName(Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val availability = if (voice.isNetworkConnectionRequired) "en línea" else "local"
    return "$localeName · Voz ${index + 1} · $availability · ${voice.name}"
}

@Composable
private fun ReadingModeCard(
    title: String,
    description: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        modifier = modifier.height(132.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            icon()
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ThemeOption(
    theme: ReaderTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val (background, foreground) = themePreviewColors(theme)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        modifier = Modifier.width(100.dp).height(80.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            Text("Aa", color = foreground, style = MaterialTheme.typography.titleLarge)
            Text(
                theme.label,
                color = foreground,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun DropdownSetting(
    label: String,
    icon: @Composable () -> Unit,
    selected: String,
    choices: List<Choice>,
    onSelected: (Choice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp)) {
            SettingLabel(label)
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    icon()
                    Spacer(Modifier.width(10.dp))
                    Text(selected, modifier = Modifier.weight(1f), maxLines = 1)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(.86f)
                ) {
                    choices.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label) },
                            trailingIcon = {
                                if (choice.label == selected) Icon(Icons.Default.Check, null)
                            },
                            onClick = {
                                onSelected(choice)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Text(valueLabel, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
        }
    }
}

private fun themePreviewColors(theme: ReaderTheme): Pair<Color, Color> = when (theme) {
    ReaderTheme.PAPER -> Color(0xFFFAF9F5) to Color(0xFF242522)
    ReaderTheme.SEPIA -> Color(0xFFF3E6C8) to Color(0xFF443827)
    ReaderTheme.SAND -> Color(0xFFE8D6B5) to Color(0xFF3D3326)
    ReaderTheme.MINT -> Color(0xFFE3F0E7) to Color(0xFF233C30)
    ReaderTheme.LAVENDER -> Color(0xFFEDE9FF) to Color(0xFF302A4A)
    ReaderTheme.ROSE -> Color(0xFFFFE9EC) to Color(0xFF4A2931)
    ReaderTheme.SKY -> Color(0xFFE5F3FA) to Color(0xFF203744)
    ReaderTheme.SLATE -> Color(0xFF26323A) to Color(0xFFDDE7EC)
    ReaderTheme.NIGHT -> Color(0xFF171A18) to Color(0xFFD5D8D1)
    ReaderTheme.OLED -> Color.Black to Color(0xFFE7E7E7)
}

private fun ReaderFont.previewFamily(): FontFamily = when (this) {
    ReaderFont.SERIF,
    ReaderFont.GARAMOND,
    ReaderFont.BASKERVILLE,
    ReaderFont.PALATINO -> FontFamily.Serif
    ReaderFont.SANS,
    ReaderFont.SYSTEM,
    ReaderFont.DYSLEXIC -> FontFamily.SansSerif
    ReaderFont.MONO -> FontFamily.Monospace
}
