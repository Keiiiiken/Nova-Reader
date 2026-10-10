package io.luma.reader.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.luma.reader.AppUiState
import io.luma.reader.model.Book
import io.luma.reader.model.BookCollection
import io.luma.reader.model.LibrarySort
import io.luma.reader.model.ReaderSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val LumaViolet = Color(0xFF155EEF)
private val LumaCoral = Color(0xFF9A6B31)
private val LumaLime = Color(0xFFE8EFFD)
private val LumaInk = Color(0xFF17161C)
private val libraryCoverCache = object : LruCache<String, ImageBitmap>(24 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width * value.height * 2 / 1024).coerceAtLeast(1)
}

private enum class HomeSection(val label: String) {
    LIBRARY("Biblioteca"), COLLECTIONS("Estantes"), NOTES("Notas")
}

private const val FILTER_ALL = "all"
private const val FILTER_READING = "reading"
private const val FILTER_PENDING = "pending"
private const val FILTER_FAVORITES = "favorites"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: AppUiState,
    snackbarHost: @Composable () -> Unit,
    onImport: (List<Uri>) -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveToCollection: (String, String?) -> Unit,
    onDeleteBook: (String) -> Unit,
    onChangeCover: (String, Uri) -> Unit,
    onUpdateBookDetails: (String, String, String, Int) -> Unit,
    onAddCollection: (String, Int) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onGridView: (Boolean) -> Unit,
    onGeneralSettingsChanged: (ReaderSettings) -> Unit,
    onAppSettingsChanged: (io.luma.reader.model.AppSettings) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAnnotation: (String, io.luma.reader.model.ReaderAnnotation) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var section by rememberSaveable { mutableStateOf(HomeSection.LIBRARY) }
    var selectedShelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var showNewCollection by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showGeneralSettings by remember { mutableStateOf(false) }
    var showReadingSettings by remember { mutableStateOf(false) }
    var showDictionary by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { onImport(it) }
    val launchImport = {
        importLauncher.launch(arrayOf("application/epub+zip", "application/octet-stream"))
    }

    // The shelf detail is an in-library destination, so the system back gesture
    // should return to the shelf index before leaving the library.
    BackHandler(enabled = selectedShelfId != null) { selectedShelfId = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = snackbarHost,
        bottomBar = {
            Column {
                if (state.backup.busy) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            BackupProgress(state.backup, onOpenBackup)
                        }
                    }
                }
                LumaDock(
                    selected = section,
                    onSelected = {
                        focusManager.clearFocus()
                        // Re-entering Estantes always starts at its own index. This keeps a
                        // previously opened shelf from looking like a hidden home filter.
                        selectedShelfId = null
                        section = it
                    }
                )
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = section,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = .985f)) togetherWith fadeOut() },
            label = "main-sections",
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
        ) { destination ->
            when (destination) {
                HomeSection.LIBRARY -> LibraryDashboard(
                    state = state,
                    filter = filter,
                    query = query,
                    showSort = showSort,
                    onFilter = { filter = it },
                    onQuery = { query = it },
                    onToggleSort = { showSort = it },
                    onSort = onSort,
                    onAppSettingsChanged = onAppSettingsChanged,
                    onSettings = { showGeneralSettings = true },
                    onImport = launchImport,
                    onOpenBook = onOpenBook,
                    onBookOptions = { selectedBook = it }
                )
                HomeSection.COLLECTIONS -> CollectionsDashboard(
                    state = state,
                    selectedShelfId = selectedShelfId,
                    onOpenCollection = { selectedShelfId = it },
                    onBack = { selectedShelfId = null },
                    onAdd = { showNewCollection = true },
                    onDelete = onDeleteCollection,
                    onOpenBook = onOpenBook,
                    onBookOptions = { selectedBook = it }
                )
                HomeSection.NOTES -> NotesDashboard(state, onOpenAnnotation)
            }
        }

        AnimatedVisibility(
            visible = state.importing,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) { ImportOverlay(state.importCount) }
    }

    selectedBook?.let { book ->
        BookActionSheet(
            book = book,
            collections = state.library.collections,
            onDismiss = { selectedBook = null },
            onFavorite = {
                onToggleFavorite(book.id)
                selectedBook = null
            },
            onMove = {
                onMoveToCollection(book.id, it)
                selectedBook = null
            },
            onDelete = {
                onDeleteBook(book.id)
                selectedBook = null
            },
            onChangeCover = { uri ->
                onChangeCover(book.id, uri)
                selectedBook = null
            },
            onSaveDetails = { title, review, rating ->
                onUpdateBookDetails(book.id, title, review, rating)
                selectedBook = null
            }
        )
    }

    if (showNewCollection) {
        NewCollectionDialog(
            onDismiss = { showNewCollection = false },
            onCreate = { name, color ->
                onAddCollection(name, color)
                showNewCollection = false
            }
        )
    }

    if (showGeneralSettings) {
        GeneralSettingsSheet(
            settings = state.library.appSettings,
            onSettingsChanged = onAppSettingsChanged,
            onOpenBackup = { showGeneralSettings = false; onOpenBackup() },
            onOpenDictionary = { showGeneralSettings = false; showDictionary = true },
            onDismiss = { showGeneralSettings = false },
            onOpenReadingSettings = {
                showGeneralSettings = false
                showReadingSettings = true
            }
        )
    }

    if (showDictionary) DictionarySheet("", onDismiss = { showDictionary = false })

    if (showReadingSettings) {
        ReaderSettingsSheet(
            settings = state.library.settings,
            onSettingsChanged = onGeneralSettingsChanged,
            onDismiss = { showReadingSettings = false },
            compactPreview = false
        )
    }
}

@Composable
private fun LumaDock(selected: HomeSection, onSelected: (HomeSection) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().height(68.dp)) {
                HomeSection.entries.forEach { destination ->
                    val active = destination == selected
                    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.weight(1f).fillMaxSize()
                            .selectable(active, role = Role.Tab, onClick = { onSelected(destination) })
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
                    ) {
                        Icon(when (destination) {
                            HomeSection.LIBRARY -> Icons.Default.AutoStories
                            HomeSection.COLLECTIONS -> Icons.Default.CollectionsBookmark
                            HomeSection.NOTES -> Icons.Default.Notes
                        }, null, tint = color, modifier = Modifier.size(23.dp))
                        Text(destination.label, color = color, fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryDashboard(
    state: AppUiState, filter: String, query: String, showSort: Boolean,
    onFilter: (String) -> Unit, onQuery: (String) -> Unit, onToggleSort: (Boolean) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onAppSettingsChanged: (io.luma.reader.model.AppSettings) -> Unit,
    onSettings: () -> Unit, onImport: () -> Unit,
    onOpenBook: (String) -> Unit, onBookOptions: (Book) -> Unit
) {
    val visibleLibraryBooks = remember(state.library.books, state.library.appSettings.includeShelvedBooks) { state.library.books.filter {
        state.library.appSettings.includeShelvedBooks || it.collectionId == null
    } }
    val books = remember(visibleLibraryBooks, query, filter, state.library.sort) { visibleLibraryBooks.asSequence()
        .filter { query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true) }
        .filter { when (filter) {
            FILTER_READING -> it.progress in .001f..0.995f
            FILTER_PENDING -> it.progress < .001f
            FILTER_FAVORITES -> it.favorite
            else -> true
        } }
        .let { sequence -> when (state.library.sort) {
            LibrarySort.RECENT -> sequence.sortedByDescending { it.lastOpenedAt.coerceAtLeast(it.addedAt) }
            LibrarySort.TITLE -> sequence.sortedBy { it.title.lowercase() }
            LibrarySort.AUTHOR -> sequence.sortedBy { it.author.lowercase() }
            LibrarySort.PROGRESS -> sequence.sortedByDescending { it.progress }
        } }.toList() }
    val continueBook = remember(visibleLibraryBooks) { visibleLibraryBooks.filter { it.progress in .001f..0.995f }.maxByOrNull { it.lastOpenedAt } }
    val columns = state.library.appSettings.libraryColumns.coerceIn(2, 3)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LibraryHeader(query, onQuery, onSettings, onImport)
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    item { FilterPill("Todos", filter == FILTER_ALL) { onFilter(FILTER_ALL) } }
                    item { FilterPill("Lectura", filter == FILTER_READING) { onFilter(FILTER_READING) } }
                    item { FilterPill("Pendientes", filter == FILTER_PENDING) { onFilter(FILTER_PENDING) } }
                    item { FilterPill("Favoritos", filter == FILTER_FAVORITES) { onFilter(FILTER_FAVORITES) } }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("library_grid")
        ) {
            if (state.library.books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EditorialEmptyState(onImport) }
            } else {
                if (continueBook != null && filter == FILTER_ALL && query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("Seguir leyendo", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            ContinueReadingCard(continueBook, { onOpenBook(continueBook.id) }, { onBookOptions(continueBook) })
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("Libros", "${books.size}") {
                        Box {
                            TextButton(onClick = { onToggleSort(true) }) {
                                Text(state.library.sort.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(Icons.Default.KeyboardArrowDown, "Ordenar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(showSort, { onToggleSort(false) }) {
                                LibrarySort.entries.forEach { sort ->
                                    DropdownMenuItem(text = { Text(sort.label) },
                                        trailingIcon = { if (sort == state.library.sort) Icon(Icons.Default.Check, null) },
                                        onClick = { onSort(sort); onToggleSort(false) })
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Incluir libros en estantes") },
                                    trailingIcon = { if (state.library.appSettings.includeShelvedBooks) Icon(Icons.Default.Check, null) },
                                    onClick = {
                                        onAppSettingsChanged(state.library.appSettings.copy(includeShelvedBooks = !state.library.appSettings.includeShelvedBooks))
                                        onToggleSort(false)
                                    })
                            }
                        }
                    }
                }
                if (books.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { NoResults(query) }
                else gridItems(books, key = { it.id }) { book ->
                    ModernBookCard(book, columns == 3, { onOpenBook(book.id) }, { onBookOptions(book) })
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    query: String,
    onQuery: (String) -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f)) { SearchBar(query, onQuery) }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.MoreHoriz, "Abrir ajustes generales", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("NOVA READER", fontSize = 11.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold)
            Text("Biblioteca", style = MaterialTheme.typography.headlineMedium)
        }
        TextButton(onClick = onImport) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("Importar")
        }
    }
}

@Composable
private fun SearchBar(value: String, onValueChanged: (String) -> Unit) {
    androidx.compose.material3.TextField(
        value = value, onValueChange = onValueChanged,
        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
        trailingIcon = { if (value.isNotBlank()) IconButton(onClick = { onValueChanged("") }) { Icon(Icons.Default.Close, "Limpiar búsqueda") } },
        placeholder = { Text("Buscar libros", style = MaterialTheme.typography.bodySmall, maxLines = 1) },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true, shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().testTag("library_search")
    )
}

@Composable
private fun EditorialEmptyState(onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(20.dp))
        Text("Tu biblioteca empieza aquí", style = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Importa un EPUB para empezar a leer.\nTus libros y notas se guardan en este dispositivo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport, shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Importar un libro")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueReadingCard(book: Book, onClick: () -> Unit, onOptions: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).testTag("continue_reading").combinedClickable(
        onClick = onClick, onLongClick = onOptions, onLongClickLabel = "Opciones de ${book.title}"
    ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BookCover(book, Modifier.width(52.dp).height(78.dp), compact = true, thumbnail = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { book.progress.coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape))
                Text("${(book.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp).size(18.dp))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 8.dp))
        trailing()
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.width(IntrinsicSize.Max).selectable(selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) color else Color.Transparent))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernBookCard(book: Book, dense: Boolean, onClick: () -> Unit, onOptions: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("book_${book.id}").combinedClickable(
        onClick = onClick, onLongClick = onOptions, onLongClickLabel = "Opciones de ${book.title}"
    )) {
        Box(Modifier.fillMaxWidth().aspectRatio(.67f)) {
            BookCover(book, Modifier.fillMaxSize(), compact = dense)
            if (book.favorite) Icon(Icons.Default.Favorite, "Favorito", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomEnd = 4.dp))
                    .padding(4.dp).size(14.dp))
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOptions, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreHoriz, "Opciones de ${book.title}", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier = Modifier, compact: Boolean = false, thumbnail: Boolean = false) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = book.coverPath) {
        value = withContext(Dispatchers.IO) {
            book.coverPath?.takeIf { File(it).isFile }?.let(::decodeLibraryCover)
        }
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap, contentDescription = "Portada de ${book.title}", contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(4.dp))
        )
    } else {
        val palette = listOf(Color(0xFFDFE7EE), Color(0xFFEAE3D8), Color(0xFFDEE7DC), Color(0xFFE8DFE4))
        val background = palette[(book.title.hashCode() and Int.MAX_VALUE) % palette.size]
        Column(modifier.clip(RoundedCornerShape(4.dp)).background(background)
            .padding(if (compact) 8.dp else 14.dp),
            verticalArrangement = if (thumbnail) Arrangement.Center else Arrangement.SpaceBetween,
            horizontalAlignment = if (thumbnail) Alignment.CenterHorizontally else Alignment.Start) {
            if (!thumbnail) Box(Modifier.width(20.dp).height(2.dp).background(Color(0xFF344054)))
            Text(if (thumbnail) book.title.trim().take(1).uppercase() else book.title, color = Color(0xFF243042), fontWeight = FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontSize = if (thumbnail) 26.sp else if (compact) 12.sp else 18.sp,
                lineHeight = if (thumbnail) 30.sp else if (compact) 15.sp else 22.sp,
                maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (!thumbnail) Text(book.author, color = Color(0xFF475467), fontSize = if (compact) 8.sp else 10.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun decodeLibraryCover(path: String): ImageBitmap? = runCatching {
    libraryCoverCache.get(path)?.let { return@runCatching it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 600 || bounds.outHeight / sampleSize > 900) {
        sampleSize *= 2
    }
    BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
    )?.asImageBitmap()?.also { libraryCoverCache.put(path, it) }
}.getOrNull()

@Composable
private fun CollectionsDashboard(
    state: AppUiState,
    selectedShelfId: String?,
    onOpenCollection: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onBookOptions: (Book) -> Unit
) {
    if (selectedShelfId != null) {
        ShelfDetail(
            state = state,
            shelfId = selectedShelfId,
            onBack = onBack,
            onOpenBook = onOpenBook,
            onBookOptions = onBookOptions
        )
        return
    }
    val booksByShelf = remember(state.library.books) { state.library.books.groupBy { it.collectionId } }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PageHeader("NOVA READER", "Estantes", "Organiza tus libros en colecciones.") }
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Nuevo estante", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        if (state.library.collections.isEmpty()) {
            item { EmptyCollections(onAdd) }
        } else {
            items(state.library.collections, key = { it.id }) { collection ->
                CollectionCard(
                    collection = collection,
                    books = booksByShelf[collection.id].orEmpty(),
                    onOpen = { onOpenCollection(collection.id) },
                    onDelete = { onDelete(collection.id) }
                )
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun ShelfDetail(
    state: AppUiState,
    shelfId: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onBookOptions: (Book) -> Unit
) {
    val collection = state.library.collections.firstOrNull { it.id == shelfId }
    val books = state.library.books.filter { it.collectionId == shelfId }
    val columns = state.library.appSettings.libraryColumns.coerceIn(2, 3)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(if (columns == 3) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a estantes") }
                Column(Modifier.weight(1f)) {
                    Text("ESTANTE", color = LumaCoral, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.8.sp)
                    Text(collection?.name ?: "Sin estante", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Text("${books.size} ${if (books.size == 1) "libro" else "libros"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShelfEmptyState(collection?.name ?: "Este estante")
            }
        } else {
            gridItems(books, key = { it.id }) { book ->
                ModernBookCard(book, columns == 3, { onOpenBook(book.id) }, { onBookOptions(book) })
            }
        }
    }
}

@Composable
private fun ShelfEmptyState(shelfName: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(70.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Este estante está vacío", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
        Text(
            "Mueve una novela a $shelfName desde sus opciones.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun PageHeader(kicker: String, title: String, description: String) {
    Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
        Text(kicker, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.8.sp)
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyCollections(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text("Todavía no tienes estantes", style = MaterialTheme.typography.titleLarge)
        Text("Agrupa libros por autor, saga o tema.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd, shape = RoundedCornerShape(8.dp)) { Text("Crear un estante") }
    }
}

@Composable
private fun CollectionCard(collection: BookCollection, books: List<Book>, onOpen: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (books.isNotEmpty()) BookCover(books.first(), Modifier.width(46.dp).height(68.dp), compact = true, thumbnail = true)
            else Box(Modifier.width(46.dp).height(68.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center) { Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(collection.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${books.size} ${if (books.size == 1) "libro" else "libros"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Eliminar estante ${collection.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NotesDashboard(state: AppUiState, onOpenAnnotation: (String, io.luma.reader.model.ReaderAnnotation) -> Unit) {
    val entries = state.library.books.flatMap { book -> book.annotations.map { book to it } }
        .sortedByDescending { it.second.createdAt }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        item { PageHeader("NOVA READER", "Notas", "Tus subrayados y comentarios, en un solo lugar.") }
        if (entries.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Notes, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(16.dp))
                Text("Guarda lo que te inspira", style = MaterialTheme.typography.titleLarge)
                Text("Selecciona un fragmento dentro del lector para subrayarlo o añadir una nota.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        items(entries, key = { "${it.first.id}:${it.second.id}" }) { (book, annotation) ->
            Column(Modifier.fillMaxWidth().clickable { onOpenAnnotation(book.id, annotation) }.padding(vertical = 8.dp)) {
                Text(book.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                if (annotation.selectedText.isNotBlank()) Text(annotation.selectedText, maxLines = 6, overflow = TextOverflow.Ellipsis)
                if (annotation.note.isNotBlank()) Text(annotation.note, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
                Text("Capítulo ${annotation.chapterIndex + 1} · Ir al fragmento", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            Text("Tiempo de lectura · ${formatReadingTime(state.library.books.sumOf { it.readingTimeMillis })}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoResults(query: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape, modifier = Modifier.size(70.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(18.dp))
        Text(if (query.isBlank()) "Nada aquí todavía" else "Sin coincidencias", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
        Text("Cambia el filtro o prueba otra búsqueda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImportOverlay(count: Int) {
    Box(Modifier.fillMaxSize().background(LumaInk.copy(alpha = .46f)), contentAlignment = Alignment.Center) {
        Surface(color = LumaInk, shape = RoundedCornerShape(12.dp), shadowElevation = 20.dp) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = LumaLime, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Importando libros", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Procesando $count ${if (count == 1) "archivo" else "archivos"}", color = Color.White.copy(alpha = .6f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookActionSheet(
    book: Book,
    collections: List<BookCollection>,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onMove: (String?) -> Unit,
    onDelete: () -> Unit,
    onChangeCover: (Uri) -> Unit,
    onSaveDetails: (String, String, Int) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showShelfPicker by remember { mutableStateOf(false) }
    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onChangeCover) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(book, Modifier.width(74.dp).height(108.dp), compact = true)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(book.progress * 100).roundToInt()}% leído", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (book.rating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(book.rating) { Icon(Icons.Default.Star, null, tint = LumaCoral, modifier = Modifier.size(16.dp)) }
                            Text("  ${book.rating}/5", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            SheetAction(
                Icons.Default.AddPhotoAlternate,
                "Cambiar portada",
                { coverLauncher.launch(arrayOf("image/*")) }
            )
            SheetAction(Icons.Default.Edit, "Editar ficha, reseña y valoración", { showDetails = true })
            SheetAction(if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (book.favorite) "Quitar de favoritos" else "Guardar en favoritos", onFavorite)
            Text("ORGANIZACIÓN", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 7.dp))
            val currentShelf = collections.firstOrNull { it.id == book.collectionId }?.name ?: "Sin estante"
            SheetAction(
                Icons.Default.CollectionsBookmark,
                "Estante · $currentShelf",
                { showShelfPicker = true }
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            SheetAction(Icons.Default.DeleteOutline, "Eliminar del dispositivo", { confirmDelete = true }, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar este libro?") },
            text = { Text("Se borrará la copia privada y el progreso guardado en Nova Reader.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
    if (showDetails) {
        BookDetailsDialog(
            book = book,
            onDismiss = { showDetails = false },
            onSave = { title, review, rating ->
                onSaveDetails(title, review, rating)
                showDetails = false
            }
        )
    }
    if (showShelfPicker) {
        ShelfPickerDialog(
            collections = collections,
            selectedId = book.collectionId,
            onDismiss = { showShelfPicker = false },
            onSelect = { collectionId ->
                showShelfPicker = false
                onMove(collectionId)
            }
        )
    }
}

@Composable
private fun ShelfPickerDialog(
    collections: List<BookCollection>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(collections, query) {
        collections.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir estante", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (collections.size > 6) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar estante") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        SheetAction(
                            Icons.Default.AutoStories,
                            "Sin estante",
                            { onSelect(null) },
                            selectedId == null
                        )
                    }
                    items(visible, key = { it.id }) { collection ->
                        SheetAction(
                            Icons.Default.CollectionsBookmark,
                            collection.name,
                            { onSelect(collection.id) },
                            selectedId == collection.id
                        )
                    }
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                "No hay estantes con ese nombre.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun BookDetailsDialog(
    book: Book,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var review by remember(book.id) { mutableStateOf(book.review) }
    var rating by remember(book.id) { mutableIntStateOf(book.rating) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ficha de la novela", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre del libro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Valoración", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { value ->
                        IconButton(onClick = { rating = if (rating == value) 0 else value }) {
                            Icon(
                                if (value <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                "${value} estrellas",
                                tint = if (value <= rating) LumaCoral else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    label = { Text("Tu reseña") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, review, rating) }, enabled = title.isNotBlank()) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    checked: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (checked) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NewCollectionDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }
    val colors = listOf(LumaViolet, LumaCoral, Color(0xFF168C80), Color(0xFFCE7D21))
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CollectionsBookmark, null, tint = colors[colorIndex]) },
        title = { Text("Nuevo estante", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(18.dp))
                Text("Color", fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.forEachIndexed { index, color ->
                        Surface(
                            onClick = { colorIndex = index }, color = color, shape = CircleShape,
                            border = if (colorIndex == index) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                            modifier = Modifier.size(42.dp)
                        ) {}
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, colorIndex) }, enabled = name.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatReadingTime(milliseconds: Long): String {
    val minutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> "< 1 min"
        minutes < 60L -> "$minutes min"
        else -> "${minutes / 60L} h ${minutes % 60L} min"
    }
}
