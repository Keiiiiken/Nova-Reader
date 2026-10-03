package io.luma.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.luma.reader.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumaApp(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val activeBook = state.activeBookId?.let { id ->
        state.library.books.firstOrNull { it.id == id }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BackHandler(enabled = activeBook != null) { viewModel.closeBook() }

    Surface(modifier) {
        // Animate only actual navigation. Reading progress updates the Book object often;
        // using the full object as Crossfade's target caused a new transition while scrolling.
        Crossfade(targetState = state.activeBookId, label = "main-navigation") { activeId ->
            val book = activeId?.let { id ->
                state.library.books.firstOrNull { it.id == id }
            }
            if (activeId == null || book == null) {
                LibraryScreen(
                    state = state,
                    snackbarHost = { SnackbarHost(snackbar) },
                    onImport = viewModel::importBooks,
                    onOpenBook = viewModel::openBook,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onMoveToCollection = viewModel::moveToCollection,
                    onDeleteBook = viewModel::deleteBook,
                    onChangeCover = viewModel::updateBookCover,
                    onUpdateBookDetails = viewModel::updateBookDetails,
                    onAddCollection = viewModel::addCollection,
                    onDeleteCollection = viewModel::deleteCollection,
                    onSort = viewModel::setSort,
                    onGridView = viewModel::setGridView,
                    onGeneralSettingsChanged = viewModel::updateSettings,
                    onAppSettingsChanged = viewModel::updateAppSettings
                )
            } else {
                ReaderScreen(
                    book = book,
                    settings = (book.readerSettings ?: state.library.settings).copy(
                        brightness = state.library.settings.brightness,
                        extraDim = state.library.settings.extraDim
                    ),
                    hasCustomSettings = book.readerSettings != null,
                    onBack = viewModel::closeBook,
                    onPositionChanged = { chapter, progress ->
                        viewModel.updateReadingPosition(book.id, chapter, progress)
                    },
                    onSettingsChanged = { settings ->
                        viewModel.updateBookSettings(book.id, settings)
                    },
                    annotations = book.annotations,
                    onSaveAnnotation = { annotation ->
                        viewModel.saveAnnotation(book.id, annotation)
                    },
                    onDeleteAnnotation = { annotationId ->
                        viewModel.deleteAnnotation(book.id, annotationId)
                    },
                    onUseGlobalSettings = {
                        viewModel.updateBookSettings(book.id, null)
                    },
                    onGlobalBrightnessChanged = { brightness ->
                        viewModel.updateSettings(state.library.settings.copy(brightness = brightness))
                    },
                    onGlobalExtraDimChanged = { extraDim ->
                        viewModel.updateSettings(state.library.settings.copy(extraDim = extraDim))
                    },
                    onReadingTimeAdded = { elapsed ->
                        viewModel.addReadingTime(book.id, elapsed)
                    }
                )
            }
        }
    }
}
