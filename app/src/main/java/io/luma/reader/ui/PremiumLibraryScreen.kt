package io.luma.reader.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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

private val LumaViolet = Color(0xFF6558F5)
private val LumaVioletDark = Color(0xFF3A2DB6)
private val LumaCoral = Color(0xFFFF715B)
private val LumaLime = Color(0xFFDDF78D)
private val LumaInk = Color(0xFF17161C)
private val libraryCoverCache = object : LruCache<String, ImageBitmap>(24 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width * value.height * 2 / 1024).coerceAtLeast(1)
}

private enum class HomeSection(val label: String) {
    LIBRARY("Inicio"), COLLECTIONS("Estantes"), INSIGHTS("Ritmo")
}

private const val FILTER_ALL = "all"
private const val FILTER_READING = "reading"
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
    onAppSettingsChanged: (io.luma.reader.model.AppSettings) -> Unit
) {
    var section by remember { mutableStateOf(HomeSection.LIBRARY) }
    var selectedShelfId by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(FILTER_ALL) }
    var query by remember { mutableStateOf("") }
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var showNewCollection by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showGeneralSettings by remember { mutableStateOf(false) }
    var showReadingSettings by remember { mutableStateOf(false) }
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
            LumaDock(
                selected = section,
                onSelected = {
                    // Re-entering Estantes always starts at its own index. This keeps a
                    // previously opened shelf from looking like a hidden home filter.
                    selectedShelfId = null
                    section = it
                },
                onAction = {
                    if (section == HomeSection.COLLECTIONS) showNewCollection = true else launchImport()
                }
            )
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
                HomeSection.INSIGHTS -> ReadingInsights(state)
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
            onDismiss = { showGeneralSettings = false },
            onOpenReadingSettings = {
                showGeneralSettings = false
                showReadingSettings = true
            }
        )
    }

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
private fun LumaDock(
    selected: HomeSection,
    onSelected: (HomeSection) -> Unit,
    onAction: () -> Unit
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxWidth < 340.dp
        // On very narrow phones the labels are stacked so “Estantes” never
        // competes with the center action button for horizontal space.
        val actionSlot = if (compact) 52.dp else 64.dp
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .22f)),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth().height(70.dp)
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DockDestination(
                    selected = selected == HomeSection.LIBRARY,
                    icon = Icons.Default.Home,
                    label = HomeSection.LIBRARY.label,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                ) {
                    onSelected(HomeSection.LIBRARY)
                }
                DockDestination(
                    selected = selected == HomeSection.COLLECTIONS,
                    icon = Icons.Default.CollectionsBookmark,
                    label = HomeSection.COLLECTIONS.label,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                ) {
                    onSelected(HomeSection.COLLECTIONS)
                }
                // The action button owns a real slot in the row. Keeping it
                // inside the layout prevents it from covering “Estantes” on
                // both narrow and wide devices.
                Box(
                    Modifier.width(actionSlot).height(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FilledIconButton(
                        onClick = onAction,
                        shape = CircleShape,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            if (selected == HomeSection.COLLECTIONS) "Nuevo estante" else "Importar EPUB",
                            Modifier.size(28.dp)
                        )
                    }
                }
                DockDestination(
                    selected = selected == HomeSection.INSIGHTS,
                    icon = Icons.Default.Insights,
                    label = HomeSection.INSIGHTS.label,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                ) {
                    onSelected(HomeSection.INSIGHTS)
                }
            }
        }
    }
}

@Composable
private fun DockDestination(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dock-color"
    )
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.height(48.dp)
    ) {
        if (compact) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 1.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, label, tint = color, modifier = Modifier.size(19.dp))
                Text(
                    label,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, label, tint = color, modifier = Modifier.size(21.dp))
                Text(
                    label,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryDashboard(
    state: AppUiState,
    filter: String,
    query: String,
    showSort: Boolean,
    onFilter: (String) -> Unit,
    onQuery: (String) -> Unit,
    onToggleSort: (Boolean) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onAppSettingsChanged: (io.luma.reader.model.AppSettings) -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onBookOptions: (Book) -> Unit
) {
    val visibleLibraryBooks = state.library.books.asSequence()
        .filter { state.library.appSettings.includeShelvedBooks || it.collectionId == null }
        .toList()
    val books = visibleLibraryBooks.asSequence()
        .filter { query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true) }
        .filter {
            when (filter) {
                FILTER_READING -> it.progress in .001f..0.995f
                FILTER_FAVORITES -> it.favorite
                FILTER_ALL -> true
                else -> it.collectionId == filter
            }
        }
        .let { sequence ->
            when (state.library.sort) {
                LibrarySort.RECENT -> sequence.sortedByDescending { it.lastOpenedAt.coerceAtLeast(it.addedAt) }
                LibrarySort.TITLE -> sequence.sortedBy { it.title.lowercase() }
                LibrarySort.AUTHOR -> sequence.sortedBy { it.author.lowercase() }
                LibrarySort.PROGRESS -> sequence.sortedByDescending { it.progress }
            }
        }.toList()
    val continueBook = visibleLibraryBooks.filter { it.progress in .001f..0.995f }.maxByOrNull { it.lastOpenedAt }
    val columns = state.library.appSettings.libraryColumns.coerceIn(2, 3)

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(if (columns == 3) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { BrandHeader(onSettings) }
        item(span = { GridItemSpan(maxLineSpan) }) { SearchBar(query, onQuery) }

        if (state.library.books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EditorialEmptyState(onImport) }
        } else {
            if (continueBook != null && filter == FILTER_ALL && query.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) { ContinueReadingCard(continueBook) { onOpenBook(continueBook.id) } }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("Tu estantería", "${books.size} ${if (books.size == 1) "historia" else "historias"}") {
                    Box {
                        IconButton(onClick = { onToggleSort(true) }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                        DropdownMenu(showSort, { onToggleSort(false) }) {
                            LibrarySort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    trailingIcon = { if (sort == state.library.sort) Icon(Icons.Default.Check, null) },
                                    onClick = { onSort(sort); onToggleSort(false) }
                                )
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterPill("Todo", filter == FILTER_ALL) { onFilter(FILTER_ALL) } }
                    item { FilterPill("Leyendo", filter == FILTER_READING) { onFilter(FILTER_READING) } }
                    item { FilterPill("Favoritos", filter == FILTER_FAVORITES) { onFilter(FILTER_FAVORITES) } }
                    item {
                        FilterPill(
                            if (state.library.appSettings.includeShelvedBooks) "Incluye estantes" else "Solo sin estante",
                            !state.library.appSettings.includeShelvedBooks
                        ) {
                            onAppSettingsChanged(
                                state.library.appSettings.copy(
                                    includeShelvedBooks = !state.library.appSettings.includeShelvedBooks
                                )
                            )
                        }
                    }
                }
            }
            if (books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { NoResults(query) }
            } else {
                gridItems(books, key = { it.id }) { book ->
                    ModernBookCard(book, columns == 3, { onOpenBook(book.id) }, { onBookOptions(book) })
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(LumaCoral))
                Spacer(Modifier.width(8.dp))
                Text("LUMA", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
            Text("¿Qué vas a leer?", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp)
        }
        Surface(onClick = onSettings, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, "Abrir ajustes generales", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SearchBar(value: String, onValueChanged: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = { AnimatedVisibility(value.isNotBlank()) { IconButton(onClick = { onValueChanged("") }) { Icon(Icons.Default.Close, "Limpiar") } } },
        placeholder = { Text("Título, autor o saga") },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

@Composable
private fun EditorialEmptyState(onImport: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(top = 18.dp).height(470.dp).clip(RoundedCornerShape(36.dp))
            .background(Brush.linearGradient(listOf(LumaViolet, LumaVioletDark, Color(0xFF211954))))
    ) {
        Box(Modifier.size(210.dp).offset(x = 210.dp, y = (-45).dp).clip(CircleShape).background(LumaCoral.copy(alpha = .9f)))
        Box(Modifier.size(150.dp).offset(x = (-40).dp, y = 410.dp).clip(CircleShape).background(LumaLime.copy(alpha = .94f)))
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(color = Color.White.copy(alpha = .13f), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Default.AutoStories, null, tint = Color.White, modifier = Modifier.padding(14.dp).size(30.dp))
            }
            Column {
                Text("Tu estantería\nempieza aquí.", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, lineHeight = 38.sp)
                Spacer(Modifier.height(12.dp))
                Text("Importa tus EPUB. Sin cuentas, sin anuncios y sin perder tu lugar.", color = Color.White.copy(alpha = .76f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onImport,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LumaLime, contentColor = LumaInk),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Añadir mi primer libro", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(book: Book, onClick: () -> Unit) {
    Surface(onClick = onClick, color = LumaInk, shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.width(98.dp).height(142.dp), compact = true)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("SIGUE LEYENDO", color = LumaLime, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp)
                Spacer(Modifier.height(10.dp))
                Text(book.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = Color.White.copy(alpha = .58f), maxLines = 1)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { book.progress }, color = LumaCoral, trackColor = Color.White.copy(alpha = .14f),
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(7.dp))
                Text("${(book.progress * 100).roundToInt()}% · capítulo ${book.chapterIndex + 1}", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp))
    }
}

@Composable
private fun ModernBookCard(book: Book, dense: Boolean, onClick: () -> Unit, onOptions: () -> Unit) {
    val corner = if (dense) 16.dp else 22.dp
    Column(Modifier.fillMaxWidth().testTag("book_${book.id}")) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(.69f).shadow(if (dense) 5.dp else 10.dp, RoundedCornerShape(corner))
                .clip(RoundedCornerShape(corner)).clickable(onClick = onClick)
        ) {
            BookCover(book, Modifier.fillMaxSize())
            Surface(onClick = onOptions, shape = CircleShape, color = LumaInk.copy(alpha = .84f), modifier = Modifier.align(Alignment.TopEnd).padding(if (dense) 6.dp else 10.dp).size(if (dense) 32.dp else 38.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreHoriz, "Opciones", tint = Color.White, modifier = Modifier.size(if (dense) 19.dp else 24.dp)) }
            }
            if (book.favorite) {
                Surface(color = LumaCoral, shape = RoundedCornerShape(topEnd = 14.dp, bottomStart = 14.dp), modifier = Modifier.align(Alignment.BottomStart)) {
                    Icon(Icons.Default.Favorite, "Favorito", tint = Color.White, modifier = Modifier.padding(10.dp).size(17.dp))
                }
            }
            if (book.progress > 0f) {
                LinearProgressIndicator(
                    progress = { book.progress }, color = LumaLime, trackColor = Color.Black.copy(alpha = .2f),
                    modifier = Modifier.fillMaxWidth().height(5.dp).align(Alignment.BottomCenter)
                )
            }
        }
        Spacer(Modifier.height(if (dense) 7.dp else 10.dp))
        Text(book.title, style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (book.rating > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(book.rating) { Icon(Icons.Default.Star, null, tint = LumaCoral, modifier = Modifier.size(13.dp)) }
                Spacer(Modifier.width(3.dp))
                Text("${book.rating}/5", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier = Modifier, compact: Boolean = false) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = book.coverPath) {
        value = withContext(Dispatchers.IO) {
            book.coverPath?.takeIf { File(it).isFile }?.let(::decodeLibraryCover)
        }
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap, contentDescription = "Portada de ${book.title}", contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(if (compact) 18.dp else 22.dp))
        )
    } else {
        val seed = book.title.fold(0) { acc, char -> acc + char.code }
        val palettes = listOf(
            listOf(Color(0xFF1D1B45), Color(0xFF6958F5), Color(0xFFFF755B)),
            listOf(Color(0xFF073B4C), Color(0xFF118A8B), Color(0xFFFFD166)),
            listOf(Color(0xFF3F1D38), Color(0xFFA23E48), Color(0xFFFFC857)),
            listOf(Color(0xFF152D35), Color(0xFF457B6D), Color(0xFFDDF78D))
        )
        val colors = palettes[kotlin.math.abs(seed) % palettes.size]
        Box(modifier.clip(RoundedCornerShape(if (compact) 18.dp else 22.dp)).background(Brush.linearGradient(colors))) {
            Box(
                Modifier.size(if (compact) 90.dp else 150.dp).offset(x = if (compact) 40.dp else 80.dp, y = (-28).dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = .13f))
            )
            Column(Modifier.fillMaxSize().padding(if (compact) 11.dp else 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("LUMA / ${book.chapters.size}", color = Color.White.copy(alpha = .62f), fontWeight = FontWeight.Bold, fontSize = if (compact) 7.sp else 9.sp, letterSpacing = 1.sp)
                Column {
                    Text(
                        book.title, color = Color.White, fontWeight = FontWeight.Black,
                        fontSize = if (compact) 14.sp else 21.sp, lineHeight = if (compact) 16.sp else 23.sp,
                        maxLines = if (compact) 3 else 4, overflow = TextOverflow.Ellipsis
                    )
                    if (!compact) {
                        Spacer(Modifier.height(8.dp))
                        Text(book.author, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
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
        item { PageHeader("TU BIBLIOTECA, A TU MANERA", "Estantes", "Colecciones limpias para sagas, autores y mundos que quieras tener juntos.") }
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
                    Text("ESTANTE", color = LumaCoral, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.8.sp)
                    Text(collection?.name ?: "Sin estante", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
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
        Text("Este estante está vacío", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
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
        Text(kicker, color = LumaCoral, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.8.sp)
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyCollections(onAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = LumaLime,
        contentColor = LumaInk,
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = LumaInk, shape = CircleShape, modifier = Modifier.size(58.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White) }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Crea tu primer estante", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Agrupa novelas sin mover sus archivos.", color = LumaInk.copy(alpha = .7f))
            }
        }
    }
}

@Composable
private fun CollectionCard(collection: BookCollection, books: List<Book>, onOpen: () -> Unit, onDelete: () -> Unit) {
    val accent = listOf(LumaViolet, LumaCoral, Color(0xFF168C80), Color(0xFFCE7D21))[collection.colorIndex % 4]
    Surface(
        onClick = onOpen,
        color = accent.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < .5f) .18f else .10f),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
        modifier = Modifier.fillMaxWidth().height(134.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(126.dp).height(102.dp)) {
                if (books.isEmpty()) {
                    Surface(
                        color = accent.copy(alpha = .16f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.size(88.dp).align(Alignment.CenterStart)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CollectionsBookmark, null, tint = accent, modifier = Modifier.size(34.dp))
                        }
                    }
                } else {
                    books.take(3).forEachIndexed { index, book ->
                        BookCover(
                            book,
                            Modifier.width(58.dp).height(88.dp)
                                .offset(x = (index * 28).dp, y = (index * 5).dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            compact = true
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    collection.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "${books.size} ${if (books.size == 1) "novela" else "novelas"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Abrir", color = accent, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, "Eliminar estante", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReadingInsights(state: AppUiState) {
    val books = state.library.books
    val readingTimeMillis = books.sumOf { it.readingTimeMillis }
    val completed = books.count { it.progress >= .995f }
    val active = books.count { it.progress in .001f..0.995f }
    val timedBooks = books.filter { it.readingTimeMillis > 0L }.sortedByDescending { it.readingTimeMillis }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        item { PageHeader("TIEMPO REAL DE LECTURA", "Ritmo", "El contador avanza únicamente mientras el lector está abierto.") }
        item {
            Box(
                Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(34.dp))
                    .background(Brush.linearGradient(listOf(LumaVioletDark, LumaViolet)))
            ) {
                Box(Modifier.size(160.dp).offset(x = 240.dp, y = (-30).dp).clip(CircleShape).background(LumaCoral))
                Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, null, tint = LumaLime)
                        Spacer(Modifier.width(9.dp))
                        Text("TIEMPO ENTRE PÁGINAS", color = LumaLime, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    }
                    Column {
                        Text(formatReadingTime(readingTimeMillis), color = Color.White, fontSize = 48.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black)
                        Text("registrados dentro del lector", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("$active", "En curso", LumaLime, Modifier.weight(1f))
                MetricCard("$completed", "Terminados", Color(0xFFFFD2CB), Modifier.weight(1f))
            }
        }
        if (timedBooks.isNotEmpty()) item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f))) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Tiempo por novela", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    }
                    timedBooks.take(5).forEach { book ->
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(book.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatReadingTime(book.readingTimeMillis), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(26.dp), color = color, modifier = modifier.height(140.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(value, color = LumaInk, fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text(label, color = LumaInk.copy(alpha = .65f), fontWeight = FontWeight.Bold)
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
        Text(if (query.isBlank()) "Nada aquí todavía" else "Sin coincidencias", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text("Cambia el filtro o prueba otra búsqueda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImportOverlay(count: Int) {
    Box(Modifier.fillMaxSize().background(LumaInk.copy(alpha = .46f)), contentAlignment = Alignment.Center) {
        Surface(color = LumaInk, shape = RoundedCornerShape(26.dp), shadowElevation = 20.dp) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = LumaLime, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Construyendo tu biblioteca", color = Color.White, fontWeight = FontWeight.Black)
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
                    Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2)
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
            Text("ORGANIZACIÓN", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 7.dp))
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
            text = { Text("Se borrará la copia privada y el progreso guardado en Luma.") },
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
    var query by remember { mutableStateOf("") }
    val visible = remember(collections, query) {
        collections.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir estante", fontWeight = FontWeight.Black) },
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
        title = { Text("Ficha de la novela", fontWeight = FontWeight.Black) },
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
        title = { Text("Nuevo estante", fontWeight = FontWeight.Black) },
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
