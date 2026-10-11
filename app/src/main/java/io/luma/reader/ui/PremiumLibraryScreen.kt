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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
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
import io.luma.reader.model.*
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
    onOpenAnnotation: (String, io.luma.reader.model.ReaderAnnotation) -> Unit,
    onManage: (Int) -> Unit = {},
    onSaveVocabulary: (VocabularyWord) -> Unit = {}
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
        importLauncher.launch(arrayOf("application/epub+zip", "application/pdf", "application/vnd.comicbook+zip", "application/zip", "application/octet-stream"))
    }

    // The shelf detail is an in-library destination, so the system back gesture
    // should return to the shelf index before leaving the library.
    BackHandler(enabled = selectedShelfId != null) { selectedShelfId = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = snackbarHost,
        bottomBar = {
            Column {
                NarrationMiniPlayer(state.library, onOpenBook)
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
                    onBookOptions = { selectedBook = it },
                    onManage = { onManage(0) }
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
            onManage = { showGeneralSettings = false; onManage(it) },
            onDismiss = { showGeneralSettings = false },
            onOpenReadingSettings = {
                showGeneralSettings = false
                showReadingSettings = true
            }
        )
    }

    if (showDictionary) DictionarySheet("", onDismiss = { showDictionary = false }, onSave = onSaveVocabulary)

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
            Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 12.dp)) {
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
                        }, null, tint = color, modifier = Modifier.background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                            .padding(horizontal = 20.dp, vertical = 5.dp).size(23.dp))
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
    onOpenBook: (String) -> Unit, onBookOptions: (Book) -> Unit, onManage: () -> Unit = {}
) {
    val visibleLibraryBooks = remember(state.library.books, state.library.appSettings.includeShelvedBooks) { state.library.books.filter {
        state.library.appSettings.includeShelvedBooks || it.collectionId == null
    } }
    val books = remember(visibleLibraryBooks, query, filter, state.library.sort) { visibleLibraryBooks.asSequence()
        .filter { query.isBlank() || listOf(it.title, it.author, it.series, it.tags.joinToString()).any { value -> value.contains(query, true) } }
        .filter { when (filter) {
            FILTER_READING -> it.effectiveStatus == ReadingStatus.READING
            FILTER_PENDING -> it.effectiveStatus == ReadingStatus.PENDING
            FILTER_FAVORITES -> it.favorite
            "finished" -> it.effectiveStatus == ReadingStatus.FINISHED
            "abandoned" -> it.effectiveStatus == ReadingStatus.ABANDONED
            else -> true
        } }
        .let { sequence -> when (state.library.sort) {
            LibrarySort.RECENT -> sequence.sortedByDescending { it.lastOpenedAt.coerceAtLeast(it.addedAt) }
            LibrarySort.TITLE -> sequence.sortedBy { it.title.lowercase() }
            LibrarySort.AUTHOR -> sequence.sortedBy { it.author.lowercase() }
            LibrarySort.PROGRESS -> sequence.sortedByDescending { it.progress }
        } }.toList() }
    val continueBook = remember(visibleLibraryBooks) { visibleLibraryBooks.filter { it.effectiveStatus == ReadingStatus.READING }.maxByOrNull { it.lastOpenedAt } }
    val columns = adaptiveLibraryColumns(state.library.appSettings)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LibraryHeader(query, onQuery, onSettings, onImport, onManage)
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterPill("Todos", filter == FILTER_ALL) { onFilter(FILTER_ALL) } }
                    item { FilterPill("Lectura", filter == FILTER_READING) { onFilter(FILTER_READING) } }
                    item { FilterPill("Pendientes", filter == FILTER_PENDING) { onFilter(FILTER_PENDING) } }
                    item { FilterPill("Favoritos", filter == FILTER_FAVORITES) { onFilter(FILTER_FAVORITES) } }
                    item { FilterPill("Terminados", filter == "finished") { onFilter("finished") } }
                    item { FilterPill("Abandonados", filter == "abandoned") { onFilter("abandoned") } }
                }
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
    onImport: () -> Unit,
    onManage: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f)) { SearchBar(query, onQuery) }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, "Abrir ajustes generales", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("TU ESPACIO DE LECTURA", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold)
            Text("Biblioteca", style = MaterialTheme.typography.headlineMedium)
        }
        IconButton(onClick = onManage) { Icon(Icons.Default.Checklist, "Organizar biblioteca") }
        Button(onClick = onImport, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
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
        singleLine = true, shape = RoundedCornerShape(18.dp),
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
        Text("Importa un EPUB, PDF o CBZ para empezar a leer.\nTus libros y notas se guardan en este dispositivo.",
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
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.primaryContainer.copy(alpha = .45f))))
        .testTag("continue_reading").combinedClickable(
            onClick = onClick, onLongClick = onOptions, onLongClickLabel = "Opciones de ${book.title}"
        ).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        BookCover(book, Modifier.width(64.dp).height(96.dp), compact = true, thumbnail = true)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SEGUIR LEYENDO", style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp, color = colors.onPrimaryContainer)
            Text(book.title, style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { book.progress.coerceIn(0f, 1f) },
                    color = colors.primary, trackColor = colors.onPrimaryContainer.copy(alpha = .12f),
                    modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape))
                Text("${(book.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryContainer)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.onPrimaryContainer,
            modifier = Modifier.padding(start = 12.dp).size(20.dp))
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
    val colors = MaterialTheme.colorScheme
    Box(Modifier.clip(CircleShape).background(if (selected) colors.primaryContainer else colors.surface)
        .selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
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
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape).padding(5.dp).size(14.dp))
            IconButton(onClick = onOptions, modifier = Modifier.align(Alignment.BottomEnd).size(48.dp)) {
                Icon(Icons.Default.MoreHoriz, "Opciones de ${book.title}", tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(6.dp).size(20.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(book.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (book.progress > 0f) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(progress = { book.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.outlineVariant)
                Text("${(book.progress * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
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
            modifier = modifier.shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
        )
    } else {
        val palette = listOf(Color(0xFFDFE7EE), Color(0xFFEAE3D8), Color(0xFFDEE7DC), Color(0xFFE8DFE4))
        val background = palette[(book.title.hashCode() and Int.MAX_VALUE) % palette.size]
        Box(modifier.shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(background)) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = .3f), radius = size.width * .75f,
                    center = Offset(size.width, size.height * .25f))
                drawCircle(Color(0xFF344054).copy(alpha = .045f), radius = size.width * .7f,
                    center = Offset(size.width * .1f, size.height))
                drawLine(Color(0xFF344054).copy(alpha = .12f), Offset(5.dp.toPx(), 0f),
                    Offset(5.dp.toPx(), size.height), strokeWidth = 1.dp.toPx())
            }
            Column(Modifier.fillMaxSize().padding(if (thumbnail) 5.dp else if (compact) 12.dp else 18.dp),
                verticalArrangement = if (thumbnail) Arrangement.Center else Arrangement.SpaceBetween,
                horizontalAlignment = if (thumbnail) Alignment.CenterHorizontally else Alignment.Start) {
                if (!thumbnail) Icon(Icons.Default.AutoStories, null, tint = Color(0xFF475467), modifier = Modifier.size(16.dp))
                Text(if (thumbnail) book.title.trim().take(1).uppercase() else book.title, color = Color(0xFF243042), fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontSize = if (thumbnail) 26.sp else if (compact) 13.sp else 19.sp,
                    lineHeight = if (thumbnail) 30.sp else if (compact) 17.sp else 24.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (!thumbnail) Box(Modifier.width(24.dp).height(2.dp).background(Color(0xFF475467)))
            }
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
    val columns = adaptiveLibraryColumns(state.library.appSettings)
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
                    Text("ESTANTE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.8.sp)
                    Text(collection?.name ?: "Sin estante", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
    val tint = listOf(Color(0xFF155EEF), Color(0xFF9A6B31), Color(0xFF168C80), Color(0xFFCE7D21))
        .getOrElse(collection.colorIndex) { MaterialTheme.colorScheme.primary }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(56.dp).height(76.dp).background(tint.copy(alpha = .1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                if (books.isNotEmpty()) BookCover(books.first(), Modifier.width(40.dp).height(58.dp), compact = true, thumbnail = true)
                else Icon(Icons.Default.CollectionsBookmark, null, tint = tint)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(24.dp).height(3.dp).background(tint, CircleShape))
                Text(collection.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${books.size} ${if (books.size == 1) "libro" else "libros"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Eliminar estante ${collection.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NotesDashboard(state: AppUiState, onOpenAnnotation: (String, io.luma.reader.model.ReaderAnnotation) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var bookFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var colorFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    val entries = state.library.books.filter { bookFilter == null || it.id == bookFilter }.flatMap { book -> book.annotations.map { book to it } }
        .filter { (_, note) -> (colorFilter == null || note.colorIndex == colorFilter) && "${note.selectedText} ${note.note}".contains(query, true) }
        .sortedByDescending { it.second.createdAt }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        item { PageHeader("NOVA READER", "Notas", "Tus subrayados y comentarios, en un solo lugar.") }
        item { OutlinedTextField(query, { query = it }, label = { Text("Buscar en notas") }, modifier = Modifier.fillMaxWidth().testTag("notes_search"), singleLine = true) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = bookFilter == null, onClick = { bookFilter = null }, label = { Text("Todos los libros") }) }
            items(state.library.books.filter { it.annotations.isNotEmpty() }) { book -> FilterChip(selected = bookFilter == book.id, onClick = { bookFilter = book.id }, label = { Text(book.title) }) }
        } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = colorFilter == null, onClick = { colorFilter = null }, label = { Text("Todos los colores") }) }
            items(listOf(0 to "Amarillo", 1 to "Verde", 2 to "Lavanda", 3 to "Rosa")) { (index, title) ->
                FilterChip(selected = colorFilter == index, onClick = { colorFilter = index }, label = { Text(title) })
            }
        } }
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
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable { onOpenAnnotation(book.id, annotation) }.padding(20.dp)) {
                Text(book.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                if (annotation.selectedText.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = .5f), CircleShape))
                    Text(annotation.selectedText, maxLines = 6, overflow = TextOverflow.Ellipsis,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, style = MaterialTheme.typography.bodyLarge)
                }
                if (annotation.note.isNotBlank()) Text(annotation.note, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
                Text("Capítulo ${annotation.chapterIndex + 1} · Ir al fragmento", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
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
        MatchSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp)) {
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
            title = { Text("¿Enviar a la papelera?") },
            text = { Text("Se conservarán el libro, las notas y el progreso en la papelera durante 30 días.") },
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

@Composable
private fun adaptiveLibraryColumns(settings: AppSettings): Int {
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    if (!settings.adaptiveColumns) return settings.libraryColumns
    val width = config.screenWidthDp
    val base = if (settings.libraryColumns == 2) 185 else 125
    return (width / (base * config.fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 8)
}

@Composable
private fun NarrationMiniPlayer(library: LibrarySnapshot, onOpen: (String) -> Unit) {
    val player by io.luma.reader.audio.NarrationPlayer.state.collectAsState()
    val context = LocalContext.current
    val book = library.books.firstOrNull { it.id == player.bookId } ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onOpen(book.id) }, modifier = Modifier.weight(1f)) { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            IconButton(onClick = { io.luma.reader.audio.NarrationPlayer.command(context, if (player.playing) "pause" else "play") }) {
                Icon(if (player.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (player.playing) "Pausar narración" else "Reanudar narración")
            }
            IconButton(onClick = { io.luma.reader.audio.NarrationPlayer.command(context, "stop") }) { Icon(Icons.Default.Close, "Detener narración") }
        }
    }
}
