package io.luma.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.luma.reader.data.DictionaryEntry
import io.luma.reader.data.OfflineDictionary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DictionarySheet(initialWord: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dictionary = remember(context) { OfflineDictionary(context.applicationContext) }
    var query by remember { mutableStateOf(initialWord.trim().take(80)) }
    var entries by remember { mutableStateOf(emptyList<DictionaryEntry>()) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        entries = emptyList(); suggestions = emptyList(); error = null
        if (query.isBlank()) { loading = false; return@LaunchedEffect }
        loading = true
        delay(250)
        try { entries = dictionary.lookup(query); if (entries.isEmpty()) suggestions = dictionary.suggestions(query) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "No se pudo abrir el diccionario" }
        finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().testTag("dictionary_sheet"), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Diccionario", style = MaterialTheme.typography.headlineMedium)
                Text("Español · disponible sin conexión", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(query, { query = it.take(80) }, label = { Text("Palabra") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dictionary_query"))
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && query.isNotBlank() && entries.isEmpty() && error == null) item {
                Text("No encontramos esa palabra. Puedes buscar otra o probar una sugerencia.")
            }
            items(suggestions) { word -> Text(word, Modifier.fillMaxWidth().clickable { query = word }.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary) }
            items(entries) { entry ->
                Column(modifier = Modifier.testTag("dictionary_entry_${entry.word}"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.word, style = MaterialTheme.typography.titleLarge)
                    entry.definitions.forEachIndexed { index, sense ->
                        Text("${index + 1}. ${sense.definition}")
                    }
                    HorizontalDivider()
                }
            }
            item {
                Text("Fuente: Wikcionario en español, extraído por Kaikki/Wiktextract. Definiciones adaptadas a un índice local bajo CC BY-SA 4.0. Consulta la licencia y los créditos en Ajustes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}
