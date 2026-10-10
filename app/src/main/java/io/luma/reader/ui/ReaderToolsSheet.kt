package io.luma.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.luma.reader.model.Book
import io.luma.reader.model.ReaderBookmark
import io.luma.reader.model.ReaderTarget
import io.luma.reader.reader.BookSearch
import io.luma.reader.reader.BookSearchResult
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderToolsSheet(book: Book, onDismiss: () -> Unit, onNavigate: (ReaderTarget) -> Unit,
    onBookmarkCurrent: () -> Unit, onDeleteBookmark: (String) -> Unit, onDictionary: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<BookSearchResult>()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        results = emptyList(); error = null
        if (query.trim().length < 2) { searching = false; return@LaunchedEffect }
        searching = true
        delay(300)
        try { results = BookSearch.search(book, query) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = "No se pudo buscar en el libro: ${e.message}" }
        finally { searching = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column {
            Box(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                SettingsHeader("Explorar el libro", book.title, onDismiss, "close_reader_tools")
            }
            LazyColumn(Modifier.fillMaxWidth().testTag("reader_tools"), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(tab == 0, { tab = 0 }, label = { Text("Buscar") })
                        FilterChip(tab == 1, { tab = 1 }, label = { Text("Marcadores") })
                    }
                    TextButton(onClick = onDictionary) { Text("Abrir diccionario") }
                }
                if (tab == 0) {
                    item { OutlinedTextField(query, { query = it.take(120) }, label = { Text("Buscar en todo el libro") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("book_search")) }
                    if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    if (!searching && query.trim().length >= 2 && results.isEmpty() && error == null) item { Text("Sin coincidencias") }
                    items(results, key = { it.chapterIndex }) { result ->
                        Column(Modifier.fillMaxWidth().clickable { onNavigate(ReaderTarget(result.chapterIndex, searchQuery = query.trim())) }.padding(vertical = 8.dp)) {
                            Text(result.title, style = MaterialTheme.typography.titleMedium)
                            Text("${result.matches} ${if (result.matches == 1) "coincidencia" else "coincidencias"}", color = MaterialTheme.colorScheme.primary)
                            Text(result.excerpt, maxLines = 4)
                        }
                        HorizontalDivider()
                    }
                } else {
                    item { Button(onClick = onBookmarkCurrent, modifier = Modifier.fillMaxWidth()) { Text("Guardar posición actual") } }
                    if (book.bookmarks.isEmpty()) item { Text("Todavía no hay marcadores. Guarda una posición para volver después.") }
                    items(book.bookmarks.sortedByDescending { it.createdAt }, key = ReaderBookmark::id) { bookmark ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { onNavigate(ReaderTarget(bookmark.chapterIndex, bookmark.anchor, bookmark.progress)) }.padding(vertical = 8.dp)) {
                                Text(book.chapters.getOrNull(bookmark.chapterIndex)?.title
                                    ?: bookmark.label.ifBlank { "Capítulo ${bookmark.chapterIndex + 1}" }, maxLines = 2)
                                bookmark.anchor?.text?.takeIf { it.isNotBlank() }?.let { fragment ->
                                    Text("… $fragment", maxLines = 2, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${((bookmark.chapterIndex + bookmark.progress) / book.chapters.size.coerceAtLeast(1) * 100).toInt()}% del libro", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDeleteBookmark(bookmark.id) }) { Icon(Icons.Default.DeleteOutline, "Eliminar marcador") }
                        }
                        HorizontalDivider()
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}
