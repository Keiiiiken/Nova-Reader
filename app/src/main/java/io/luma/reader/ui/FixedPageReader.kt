package io.luma.reader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.luma.reader.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FixedPageReader(book: Book, onBack: () -> Unit, onPosition: (Int) -> Unit,
    onReadingTime: (Long) -> Unit, onBookmark: (ReaderBookmark) -> Unit) {
    val pager = rememberPagerState(initialPage = book.chapterIndex.coerceIn(book.chapters.indices), pageCount = { book.chapters.size })
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var showBookmarks by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current
    val latestTime by rememberUpdatedState(onReadingTime)
    DisposableEffect(book.id, lifecycle) {
        var started = if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) android.os.SystemClock.elapsedRealtime() else 0L
        fun flush() { if (started > 0) latestTime(android.os.SystemClock.elapsedRealtime() - started); started = 0L }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) started = android.os.SystemClock.elapsedRealtime()
            if (event == Lifecycle.Event.ON_PAUSE) flush()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { flush(); lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(pager.settledPage) { onPosition(pager.settledPage); scale = 1f }
    Scaffold(topBar = { TopAppBar(title = { Text(book.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }, actions = {
            IconButton(onClick = { onBookmark(ReaderBookmark(java.util.UUID.randomUUID().toString(), pager.currentPage, 0f, label = "Página ${pager.currentPage + 1}")) }) { Icon(Icons.Default.BookmarkAdd, "Guardar página") }
            IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Default.Bookmarks, "Ver marcadores") }
        }) }, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Página ${pager.currentPage + 1} de ${book.chapters.size}", Modifier.weight(1f))
                IconButton(onClick = { scale = (scale - .5f).coerceAtLeast(1f) }) { Icon(Icons.Default.ZoomOut, "Reducir zoom") }
                IconButton(onClick = { scale = (scale + .5f).coerceAtMost(4f) }) { Icon(Icons.Default.ZoomIn, "Ampliar página") }
            }
            if (book.chapters.size > 1) Slider(value = pager.currentPage.toFloat(), onValueChange = { value -> scope.launch { pager.scrollToPage(value.toInt()) } }, valueRange = 0f..book.chapters.lastIndex.toFloat())
        }
    }) { padding ->
        HorizontalPager(state = pager, userScrollEnabled = scale == 1f, modifier = Modifier.fillMaxSize().padding(padding).testTag("fixed_page_reader")) { page ->
            var bitmap by remember(book.id, page) { mutableStateOf<Bitmap?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(book.id, page) {
                try { bitmap = withContext(Dispatchers.IO) { renderBookPage(book, page) } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { error = "No se pudo mostrar esta página" }
            }
            // Let the image's native allocation be collected after Compose releases it.
            // Recycling during pager disposal can race a frame still using its texture.
            var x by remember(page) { mutableFloatStateOf(0f) }; var y by remember(page) { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                bitmap?.let { image ->
                    Image(image.asImageBitmap(), "Página ${page + 1}", Modifier.fillMaxSize()
                        .transformable(state = rememberTransformableState { zoom, pan, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            x = if (scale == 1f) 0f else (x + pan.x).coerceIn(-1000f, 1000f)
                            y = if (scale == 1f) 0f else (y + pan.y).coerceIn(-1600f, 1600f)
                        }, canPan = { scale > 1f }).graphicsLayer { scaleX = scale; scaleY = scale; translationX = x; translationY = y })
                } ?: if (error == null) CircularProgressIndicator() else Text(error!!)
            }
        }
    }
    if (showBookmarks) AlertDialog(onDismissRequest = { showBookmarks = false }, title = { Text("Marcadores") }, text = {
        androidx.compose.foundation.lazy.LazyColumn {
            if (book.bookmarks.isEmpty()) item { Text("Todavía no has guardado páginas") }
            items(book.bookmarks.size) { index -> val mark = book.bookmarks[index]
                TextButton(onClick = { scope.launch { pager.scrollToPage(mark.chapterIndex.coerceIn(book.chapters.indices)) }; showBookmarks = false }) { Text(mark.label) }
            }
        }
    }, confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text("Cerrar") } })
}

internal fun renderBookPage(book: Book, page: Int): Bitmap = if (book.format == BookFormat.PDF) {
    PdfRenderer(ParcelFileDescriptor.open(File(book.epubPath), ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
        renderer.openPage(page).use { item ->
            val width = 1600
            val height = (width.toFloat() * item.height / item.width).toInt().coerceIn(1, 2400)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE)
                item.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
} else {
    val file = File(book.contentPath, book.chapters[page].href)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (bounds.outWidth / inSampleSize > 2000 || bounds.outHeight / inSampleSize > 2400) inSampleSize *= 2
    }
    BitmapFactory.decodeFile(file.path, options) ?: error("Imagen inválida")
}
