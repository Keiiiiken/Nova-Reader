package io.luma.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.luma.reader.MainViewModel
import io.luma.reader.model.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LibraryManager(library: LibrarySnapshot, vm: MainViewModel, initialTab: Int, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var query by rememberSaveable { mutableStateOf("") }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var action by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Book?>(null) }
    var shelf by remember { mutableStateOf<BookCollection?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    BackHandler { if (selection.isNotEmpty()) selection = emptySet() else onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text("Tu biblioteca") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a la biblioteca") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("library_manager")) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                listOf("Organizar", "Estantes", "Papelera", "Vocabulario", "Actividad").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index; selection = emptySet(); query = "" }, text = { Text(title) })
                }
            }
            if (tab != 4) OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("manager_search"))
            if (tab == 0 || tab == 2) {
                val books = library.books.filter { (it.deletedAt > 0) == (tab == 2) &&
                    listOf(it.title, it.author, it.series, it.tags.joinToString()).any { value -> value.contains(query, true) } }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selection = if (selection.containsAll(books.map { it.id })) emptySet() else books.map { it.id }.toSet() }) {
                        Text(if (selection.isEmpty()) "Seleccionar todos" else "${selection.size} seleccionados")
                    }
                    Spacer(Modifier.weight(1f))
                    if (selection.isNotEmpty()) Box {
                        FilledTonalButton(onClick = { action = "menu" }) { Text("Acciones") }
                        DropdownMenu(expanded = action == "menu", onDismissRequest = { action = null }) {
                            if (tab == 2) {
                                DropdownMenuItem(text = { Text("Restaurar") }, onClick = { vm.restoreBooks(selection); selection = emptySet(); action = null })
                                DropdownMenuItem(text = { Text("Eliminar definitivamente") }, onClick = { confirmErase = true; action = null })
                            } else {
                                DropdownMenuItem(text = { Text("Cambiar estado") }, onClick = { action = "status" })
                                DropdownMenuItem(text = { Text("Mover a estante") }, onClick = { action = "shelf" })
                                DropdownMenuItem(text = { Text("Marcar favoritos") }, onClick = { vm.favoriteBooks(selection, true); action = null })
                                DropdownMenuItem(text = { Text("Quitar favoritos") }, onClick = { vm.favoriteBooks(selection, false); action = null })
                                DropdownMenuItem(text = { Text("Enviar a papelera") }, onClick = { vm.trashBooks(selection); selection = emptySet(); action = null })
                            }
                        }
                    }
                }
                if (tab == 2) Text("Recupera tus libros durante 30 días. Después se eliminan al abrir Nova Reader.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (books.isEmpty()) item { Text(if (tab == 2) "La papelera está vacía" else "No hay libros que coincidan") }
                    items(books, key = { it.id }) { book ->
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().clickable { selection = if (book.id in selection) selection - book.id else selection + book.id }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = book.id in selection, onCheckedChange = { selection = if (it) selection + book.id else selection - book.id }, modifier = Modifier.testTag("select_${book.id}"))
                                Column(Modifier.weight(1f).padding(8.dp)) {
                                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                                    Text(book.author, style = MaterialTheme.typography.bodySmall)
                                    Text("${book.effectiveStatus.label} · ${book.format}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    if (book.series.isNotBlank()) Text("${book.series} ${book.volume}", style = MaterialTheme.typography.bodySmall)
                                    if (book.tags.isNotEmpty()) Text(book.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                }
                                if (tab == 0) IconButton(onClick = { editing = book }) { Icon(Icons.Default.Edit, "Editar ${book.title}") }
                            }
                        }
                    }
                }
            } else if (tab == 1) LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(library.collections.filter { it.name.contains(query, true) }, key = { it.id }) { item ->
                    ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("${library.books.count { it.collectionId == item.id && it.deletedAt == 0L }} libros") },
                        trailingContent = { IconButton(onClick = { shelf = item }) { Icon(Icons.Default.Edit, "Editar estante ${item.name}") } })
                }
            } else if (tab == 3) LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val words = library.vocabulary.filter { "${it.word} ${it.definition} ${it.quote} ${it.bookTitle}".contains(query, true) }.sortedByDescending { it.createdAt }
                if (words.isEmpty()) item { Text("Guarda palabras desde el diccionario para encontrarlas aquí.") }
                items(words, key = { it.id }) { word ->
                    ElevatedCard { Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(word.word, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.deleteVocabulary(word.id) }) { Icon(Icons.Default.DeleteOutline, "Eliminar palabra ${word.word}") }
                        }
                        Text(word.definition)
                        if (word.quote.isNotBlank()) Text("«${word.quote}»", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        if (word.bookTitle.isNotBlank()) Text(word.bookTitle, style = MaterialTheme.typography.labelMedium)
                    } }
                }
            } else if (tab == 4) ReadingActivity(library) { vm.updateAppSettings(library.appSettings.copy(dailyGoalMinutes = it)) }
        }
    }
    if (action == "status") AlertDialog(onDismissRequest = { action = null }, title = { Text("Estado de lectura") }, text = {
        Column { ReadingStatus.entries.forEach { status -> TextButton(onClick = { vm.setBookStatus(selection, status); action = null }) { Text(status.label) } } }
    }, confirmButton = { TextButton(onClick = { action = null }) { Text("Cancelar") } })
    if (action == "shelf") AlertDialog(onDismissRequest = { action = null }, title = { Text("Mover a estante") }, text = {
        LazyColumn { item { TextButton(onClick = { vm.moveBooks(selection, null); action = null }) { Text("Sin estante") } }
            items(library.collections) { item -> TextButton(onClick = { vm.moveBooks(selection, item.id); action = null }) { Text(item.name) } } }
    }, confirmButton = { TextButton(onClick = { action = null }) { Text("Cancelar") } })
    if (confirmErase) AlertDialog(onDismissRequest = { confirmErase = false }, title = { Text("¿Eliminar definitivamente?") }, text = { Text("Se eliminarán ${selection.size} libros con sus notas y progreso. Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = { vm.deletePermanently(selection); selection = emptySet(); confirmErase = false }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } })
    editing?.let { book -> MetadataEditor(book, onDismiss = { editing = null }) { title, author, series, volume, tags ->
        vm.updateMetadata(book.id, title, author, series, volume, tags); editing = null
    } }
    shelf?.let { item ->
        var name by remember(item.id) { mutableStateOf(item.name) }
        var color by remember(item.id) { mutableIntStateOf(item.colorIndex) }
        AlertDialog(onDismissRequest = { shelf = null }, title = { Text("Editar estante") }, text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
            FlowRow { listOf("Violeta", "Azul", "Verde", "Rosa", "Ámbar", "Gris").forEachIndexed { i, label ->
                FilterChip(selected = color == i, onClick = { color = i }, label = { Text(label) }, modifier = Modifier.padding(end = 4.dp))
            } }
        } }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.renameCollection(item.id, name, color); shelf = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { shelf = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun MetadataEditor(book: Book, onDismiss: () -> Unit, onSave: (String, String, String, String, List<String>) -> Unit) {
    var title by remember { mutableStateOf(book.title) }; var author by remember { mutableStateOf(book.author) }
    var series by remember { mutableStateOf(book.series) }; var volume by remember { mutableStateOf(book.volume) }
    var tags by remember { mutableStateOf(book.tags.joinToString(", ")) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ficha del libro") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(title, { title = it }, label = { Text("Título") }) }
            item { OutlinedTextField(author, { author = it }, label = { Text("Autor") }) }
            item { OutlinedTextField(series, { series = it }, label = { Text("Saga") }) }
            item { OutlinedTextField(volume, { volume = it }, label = { Text("Volumen") }) }
            item { OutlinedTextField(tags, { tags = it }, label = { Text("Etiquetas separadas por comas") }) }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, author, series, volume, tags.split(',')) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingActivity(library: LibrarySnapshot, onGoal: (Int) -> Unit) {
    val today = LocalDate.now()
    val days = library.readingDays.associate { it.date to it.millis }
    val goal = library.appSettings.dailyGoalMinutes
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Tu ritmo de lectura", style = MaterialTheme.typography.headlineSmall)
            Text("Hoy: ${(days[today.toString()] ?: 0L) / 60000} min · Total: ${library.books.sumOf { it.readingTimeMillis } / 60000} min") }
        item { Text("Meta diaria opcional", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 10, 20, 30, 60).forEach { minutes -> FilterChip(selected = goal == minutes, onClick = { onGoal(minutes) }, label = { Text(if (minutes == 0) "Sin meta" else "$minutes min") }) }
            }
        }
        item { Text("Últimos 28 días", style = MaterialTheme.typography.titleMedium) }
        items((0..3).toList()) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).forEach { day ->
                    val date = today.minusDays((27 - week * 7 - day).toLong())
                    val minutes = (days[date.toString()] ?: 0L) / 60000
                    Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                        color = if (minutes > 0 && (goal == 0 || minutes >= goal)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelMedium)
                            Text("${minutes}m", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item { Text("El historial diario comienza con esta actualización. El tiempo acumulado de tus libros anteriores se conserva.", style = MaterialTheme.typography.bodySmall) }
    }
}
