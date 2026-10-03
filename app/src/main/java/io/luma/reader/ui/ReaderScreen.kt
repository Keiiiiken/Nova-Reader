package io.luma.reader.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.luma.reader.epub.EpubParser
import io.luma.reader.model.Book
import io.luma.reader.model.ReaderAnnotation
import io.luma.reader.model.ReaderFont
import io.luma.reader.model.ReaderAlignment
import io.luma.reader.model.ReaderSettings
import io.luma.reader.model.ReaderTheme
import io.luma.reader.model.ReadingMode
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private data class ReadingColors(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val surface: Color,
    val cssBackground: String,
    val cssForeground: String
)

private data class SelectionData(
    val text: String,
    val blockIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val endBlockIndex: Int = blockIndex
)

private data class AnnotationDraft(
    val selectedText: String,
    val blockIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val endBlockIndex: Int = blockIndex,
    val annotationId: String? = null,
    val initialNote: String = "",
    val initialColorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

private data class SpeechChunk(
    val text: String,
    val pauseAfterMs: Int,
    val blockIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val sourceOffsets: IntArray
)

private data class SpeechBlock(
    val blockIndex: Int,
    val text: String
)

private val ReaderAccent = Color(0xFF6558F5)
private val ReaderCoral = Color(0xFFFF715B)
private val ReaderInk = Color(0xFF17161C)

private class ReaderWebView(context: Context) : WebView(context) {
    var readingMode: ReadingMode = ReadingMode.SCROLL
    var pendingProgress: Float = 0f
    var reportProgress: (Float) -> Unit = {}
    var annotationsJson: String = "[]"
    var reportSelection: (SelectionData?) -> Unit = {}
    private var appliedAnnotationsJson: String? = null

    init {
        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onSelection(payload: String) {
                    post { reportSelection(payload.toSelectionData()) }
                }
            },
            "LumaSelection"
        )
    }

    fun captureSelection(callback: (SelectionData?) -> Unit) {
        evaluateJavascript("window.LumaAnnotations ? window.LumaAnnotations.captureSelection() : ''") { raw ->
            val value = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            callback(value.toSelectionData())
        }
    }

    fun clearTextSelection() {
        evaluateJavascript("window.getSelection && window.getSelection().removeAllRanges()", null)
        reportSelection(null)
    }

    fun captureSpeechBlocks(callback: (List<SpeechBlock>) -> Unit) {
        evaluateJavascript(
            "window.LumaNarration ? window.LumaNarration.blocks() : '[]'"
        ) { raw ->
            val value = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            post { callback(value.toSpeechBlocks()) }
        }
    }

    fun showNarrationPosition(
        blockIndex: Int,
        startOffset: Int,
        endOffset: Int,
        follow: Boolean
    ) {
        evaluateJavascript(
            "window.LumaNarration && window.LumaNarration.highlight(" +
                "$blockIndex,$startOffset,$endOffset,${if (follow) "true" else "false"})",
            null
        )
    }

    fun clearNarrationPosition() {
        evaluateJavascript("window.LumaNarration && window.LumaNarration.clear()", null)
    }

    fun applyAnnotations(force: Boolean = false) {
        if (!force && appliedAnnotationsJson == annotationsJson) return
        appliedAnnotationsJson = annotationsJson
        evaluateJavascript(
            "window.LumaAnnotations && window.LumaAnnotations.apply(${JSONObject.quote(annotationsJson)})",
            null
        )
    }

    fun scrollToAnnotation(id: String) {
        evaluateJavascript(
            "window.LumaAnnotations && window.LumaAnnotations.scrollTo(${JSONObject.quote(id)})",
            null
        )
    }

    private fun horizontalRange(): Int =
        (computeHorizontalScrollRange() - width).coerceAtLeast(0)

    private fun verticalRange(): Int =
        (computeVerticalScrollRange() - height).coerceAtLeast(0)

    fun currentProgress(): Float {
        val range = if (readingMode == ReadingMode.PAGED) horizontalRange() else verticalRange()
        val position = if (readingMode == ReadingMode.PAGED) scrollX else scrollY
        return if (range <= 0) 0f else (position.toFloat() / range).coerceIn(0f, 1f)
    }

    fun restoreProgress() {
        postDelayed({
            if (readingMode == ReadingMode.PAGED) {
                scrollTo((horizontalRange() * pendingProgress.coerceIn(0f, 1f)).roundToInt(), 0)
            } else {
                scrollTo(0, (verticalRange() * pendingProgress.coerceIn(0f, 1f)).roundToInt())
            }
        }, 140)
    }

    fun movePage(direction: Int): Boolean {
        if (readingMode != ReadingMode.PAGED || width <= 0) return false
        val range = horizontalRange()
        val target = (scrollX + direction * width).coerceIn(0, range)
        if (target == scrollX) return false
        scrollTo(target, 0)
        post { reportProgress(currentProgress()) }
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    settings: ReaderSettings,
    hasCustomSettings: Boolean,
    onBack: () -> Unit,
    onPositionChanged: (Int, Float) -> Unit,
    onSettingsChanged: (ReaderSettings) -> Unit,
    annotations: List<ReaderAnnotation>,
    onSaveAnnotation: (ReaderAnnotation) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onUseGlobalSettings: () -> Unit,
    onGlobalBrightnessChanged: (Float) -> Unit,
    onGlobalExtraDimChanged: (Float) -> Unit,
    onReadingTimeAdded: (Long) -> Unit
) {
    var chapterIndex by remember(book.id) { mutableIntStateOf(book.chapterIndex.coerceIn(0, book.chapters.lastIndex)) }
    var chapterProgress by remember(book.id) { mutableFloatStateOf(book.chapterProgress) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var annotationDraft by remember { mutableStateOf<AnnotationDraft?>(null) }
    var pendingSelection by remember { mutableStateOf<SelectionData?>(null) }
    var webView by remember { mutableStateOf<ReaderWebView?>(null) }
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = readingColors(settings.theme)
    val chapter = book.chapters[chapterIndex]

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsInitRequested by remember { mutableStateOf(false) }
    var pendingNarrationStart by remember { mutableStateOf(false) }
    var pendingNarrationSelection by remember { mutableStateOf<SelectionData?>(null) }
    var speaking by remember { mutableStateOf(false) }
    var narrationChunks by remember(book.id) { mutableStateOf(emptyList<SpeechChunk>()) }
    var narrationChapter by remember(book.id) { mutableIntStateOf(-1) }
    var narrationChunk by remember(book.id) { mutableIntStateOf(0) }
    var narrationOffset by remember(book.id) { mutableIntStateOf(0) }
    var narrationEndOffset by remember(book.id) { mutableIntStateOf(0) }
    var narrationPaused by remember(book.id) { mutableStateOf(false) }
    var narrationAutoAdvanceTarget by remember(book.id) { mutableIntStateOf(-1) }
    var narrationStartWhenPageReady by remember(book.id) { mutableStateOf(false) }
    var lastNarrationBlock by remember(book.id) { mutableIntStateOf(-1) }
    val latestReaderSettings = rememberUpdatedState(settings)
    val latestChapterIndex = rememberUpdatedState(chapterIndex)
    val readingTimer = remember(book.id) { longArrayOf(0L) }
    val latestReadingTimeAdded = rememberUpdatedState(onReadingTimeAdded)

    LaunchedEffect(book.id) {
        while (true) {
            delay(30_000L)
            if (readingTimer[0] > 0L) {
                val now = SystemClock.elapsedRealtime()
                latestReadingTimeAdded.value((now - readingTimer[0]).coerceAtLeast(0L))
                readingTimer[0] = now
            }
        }
    }

    DisposableEffect(lifecycleOwner, book.id) {
        fun startTimer() {
            if (readingTimer[0] == 0L) readingTimer[0] = SystemClock.elapsedRealtime()
        }

        fun flushTimer() {
            val startedAt = readingTimer[0]
            if (startedAt == 0L) return
            latestReadingTimeAdded.value(
                (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            )
            readingTimer[0] = 0L
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    startTimer()
                    if ((speaking || narrationPaused) && narrationChapter == chapterIndex) {
                        narrationChunks.getOrNull(narrationChunk)?.let { chunk ->
                            webView?.showNarrationPosition(
                                blockIndex = chunk.blockIndex,
                                startOffset = chunk.domOffset(narrationOffset),
                                endOffset = chunk.endOffset,
                                follow = true
                            )
                            lastNarrationBlock = chunk.blockIndex
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> flushTimer()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            startTimer()
        }
        onDispose {
            flushTimer()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(settings.ttsEngine, ttsInitRequested) {
        ttsReady = false
        val engine = if (!ttsInitRequested) null else if (settings.ttsEngine == null) {
            TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        } else {
            TextToSpeech(
                context,
                { status -> ttsReady = status == TextToSpeech.SUCCESS },
                settings.ttsEngine
            )
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val position = utteranceId.narrationPosition()
                activity.runOnUiThread {
                    position?.let { (chapter, chunk, offset) ->
                        if (chapter == latestChapterIndex.value) {
                            narrationChapter = chapter
                            narrationChunk = chunk
                            narrationOffset = offset
                            narrationEndOffset = narrationChunks.getOrNull(chunk)?.text?.length ?: offset
                        }
                    }
                    if (!narrationPaused) speaking = true
                }
            }

            override fun onDone(utteranceId: String?) {
                val position = utteranceId.narrationPosition()
                activity.runOnUiThread {
                    // stop() can still dispatch onDone. Keep the exact chunk
                    // captured for pause/resume instead of advancing it.
                    if (!narrationPaused) {
                        position?.let { (_, chunk, _) ->
                            narrationChunk = (chunk + 1).coerceAtMost(narrationChunks.lastIndex.coerceAtLeast(0))
                            narrationOffset = 0
                            narrationEndOffset = narrationChunks.getOrNull(narrationChunk)?.text?.length ?: 0
                        }
                        if (utteranceId?.endsWith("_final") == true) {
                            val completedChapter = position?.first ?: latestChapterIndex.value
                            if (
                                latestReaderSettings.value.ttsAutoAdvance &&
                                completedChapter < book.chapters.lastIndex
                            ) {
                                narrationAutoAdvanceTarget = completedChapter + 1
                            } else {
                                speaking = false
                                narrationPaused = false
                                narrationChunk = 0
                                narrationOffset = 0
                                narrationEndOffset = 0
                                lastNarrationBlock = -1
                                webView?.clearNarrationPosition()
                            }
                        }
                    }
                }
            }

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                val position = utteranceId.narrationPosition() ?: return
                activity.runOnUiThread {
                    val (chapter, chunk, baseOffset) = position
                    if (!narrationPaused && chapter == latestChapterIndex.value) {
                        narrationChapter = chapter
                        narrationChunk = chunk
                        narrationOffset = baseOffset + start
                        narrationEndOffset = baseOffset + end
                    }
                }
            }

            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                activity.runOnUiThread {
                    speaking = false
                    webView?.clearNarrationPosition()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                activity.runOnUiThread {
                    speaking = false
                    webView?.clearNarrationPosition()
                }
            }
        })
        tts = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
            if (tts === engine) tts = null
        }
    }

    LaunchedEffect(
        speaking,
        narrationPaused,
        narrationChapter,
        narrationChunk,
        narrationOffset,
        narrationEndOffset,
        webView,
        chapterIndex
    ) {
        if ((speaking || narrationPaused) && narrationChapter == chapterIndex) {
            narrationChunks.getOrNull(narrationChunk)?.let { chunk ->
                val shouldFollow = chunk.blockIndex != lastNarrationBlock
                webView?.showNarrationPosition(
                    blockIndex = chunk.blockIndex,
                    startOffset = chunk.domOffset(narrationOffset),
                    endOffset = if (narrationEndOffset > narrationOffset) {
                        chunk.domOffset(narrationEndOffset)
                    } else chunk.endOffset,
                    follow = shouldFollow
                )
                lastNarrationBlock = chunk.blockIndex
            }
        }
    }

    DisposableEffect(settings.keepScreenOn) {
        if (settings.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(activity) {
        val previousBrightness = activity.window.attributes.screenBrightness
        onDispose {
            val attributes = activity.window.attributes
            attributes.screenBrightness = previousBrightness
            activity.window.attributes = attributes
        }
    }

    SideEffect {
        val attributes = activity.window.attributes
        attributes.screenBrightness = settings.brightness
        activity.window.attributes = attributes
        activity.window.statusBarColor = colors.background.toArgbCompat()
        activity.window.navigationBarColor = colors.background.toArgbCompat()
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            val light = settings.theme !in setOf(
                ReaderTheme.NIGHT, ReaderTheme.SLATE, ReaderTheme.OLED
            )
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    LaunchedEffect(controlsVisible, showSettings, showContents, speaking) {
        if (controlsVisible && !showSettings && !showContents && !speaking) {
            delay(5_000)
            controlsVisible = false
        }
    }

    // Persist only after scrolling has settled. The old implementation wrote JSON to
    // SharedPreferences for every scroll callback, which caused visible jank.
    LaunchedEffect(chapterIndex, chapterProgress) {
        delay(650)
        onPositionChanged(chapterIndex, chapterProgress)
    }

    BackHandler(enabled = showSettings || showContents || showAnnotations || annotationDraft != null) {
        showSettings = false
        showContents = false
        showAnnotations = false
        annotationDraft = null
    }

    fun changeChapter(newIndex: Int, initialProgress: Float = 0f) {
        if (newIndex !in book.chapters.indices) return
        tts?.stop()
        webView?.clearNarrationPosition()
        speaking = false
        narrationPaused = false
        narrationChunks = emptyList()
        narrationChapter = -1
        narrationChunk = 0
        narrationOffset = 0
        narrationEndOffset = 0
        narrationAutoAdvanceTarget = -1
        narrationStartWhenPageReady = false
        lastNarrationBlock = -1
        chapterIndex = newIndex
        pendingSelection = null
        chapterProgress = initialProgress.coerceIn(0f, 1f)
        onPositionChanged(newIndex, chapterProgress)
        controlsVisible = true
    }

    fun enqueueNarration(engine: TextToSpeech, startChunk: Int, startOffset: Int) {
        if (narrationChunks.isEmpty() || startChunk !in narrationChunks.indices) return
        engine.stop()
        var queued = false
        for (index in startChunk..narrationChunks.lastIndex) {
            val chunk = narrationChunks[index]
            val offset = if (index == startChunk) {
                startOffset.coerceIn(0, chunk.text.length)
            } else 0
            val part = chunk.text.substring(offset)
            if (part.isBlank()) continue
            val parameters = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.ttsVolume)
            }
            val suffix = if (index == narrationChunks.lastIndex) "final" else "part"
            engine.speak(
                part,
                if (!queued) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                parameters,
                "luma_${chapterIndex}_${index}_${offset}_$suffix"
            )
            queued = true
            if (index < narrationChunks.lastIndex && chunk.pauseAfterMs > 0) {
                engine.playSilentUtterance(
                    chunk.pauseAfterMs.toLong(),
                    TextToSpeech.QUEUE_ADD,
                    "luma_pause_${chapterIndex}_$index"
                )
            }
        }
        if (queued) {
            narrationPaused = false
            speaking = true
        }
    }

    fun beginNarration(
        engine: TextToSpeech,
        selection: SelectionData? = null,
        startFromProgress: Boolean = true
    ) {
        val requestedChapter = chapterIndex
        val readerView = webView ?: return
        readerView.captureSpeechBlocks { blocks ->
            if (requestedChapter != chapterIndex) return@captureSpeechBlocks
            val chunks = blocks.chunkForSpeech(
                sentencePauseMs = settings.ttsSentencePauseMs,
                paragraphPauseMs = settings.ttsParagraphPauseMs
            )
            if (chunks.isEmpty()) {
                if (settings.ttsAutoAdvance && chapterIndex < book.chapters.lastIndex) {
                    speaking = true
                    narrationAutoAdvanceTarget = chapterIndex + 1
                } else {
                    speaking = false
                    readerView.clearNarrationPosition()
                }
                return@captureSpeechBlocks
            }
            narrationChunks = chunks
            narrationChapter = requestedChapter
            val startChunk = when {
                selection != null -> chunks.indexOfFirst { chunk ->
                    chunk.blockIndex > selection.blockIndex ||
                        (chunk.blockIndex == selection.blockIndex && chunk.endOffset > selection.startOffset)
                }.takeIf { it >= 0 } ?: chunks.lastIndex
                startFromProgress -> (chunks.size * chapterProgress)
                    .toInt().coerceIn(0, chunks.lastIndex)
                else -> 0
            }
            val startOffset = selection?.let { selected ->
                val chunk = chunks[startChunk]
                if (chunk.blockIndex == selected.blockIndex) {
                    chunk.speechOffsetForDom(selected.startOffset)
                } else 0
            } ?: 0
            narrationChunk = startChunk
            narrationOffset = startOffset
            narrationEndOffset = chunks[startChunk].text.length
            lastNarrationBlock = -1
            enqueueNarration(engine, startChunk, startOffset)
        }
    }

    fun startNarration(selection: SelectionData? = null) {
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingNarrationStart = true
            pendingNarrationSelection = selection
            ttsInitRequested = true
            return
        }
        if (speaking && selection == null) {
            // Mark the pause before stop(): some engines deliver a late
            // onDone callback and must not move us to the next sentence.
            narrationPaused = true
            narrationAutoAdvanceTarget = -1
            engine.stop()
            speaking = false
            return
        }
        if (selection != null) {
            engine.stop()
            narrationPaused = false
            narrationAutoAdvanceTarget = -1
        }
        val selectedVoice = settings.ttsVoiceName?.let { voiceName ->
            engine.voices?.firstOrNull { it.name == voiceName }
        }
        if (selectedVoice != null) engine.voice = selectedVoice
        else engine.language = if (settings.ttsLanguageTag == "system") Locale.getDefault()
        else Locale.forLanguageTag(settings.ttsLanguageTag)
        engine.setSpeechRate(settings.ttsRate)
        engine.setPitch(settings.ttsPitch)

        if (
            selection == null && narrationPaused && narrationChapter == chapterIndex &&
            narrationChunks.isNotEmpty()
        ) {
            enqueueNarration(engine, narrationChunk, narrationOffset)
            return
        }
        beginNarration(engine, selection)
    }

    LaunchedEffect(ttsReady, pendingNarrationStart) {
        if (ttsReady && pendingNarrationStart) {
            pendingNarrationStart = false
            val selection = pendingNarrationSelection
            pendingNarrationSelection = null
            startNarration(selection)
        }
    }

    LaunchedEffect(narrationAutoAdvanceTarget) {
        val firstTarget = narrationAutoAdvanceTarget
        if (firstTarget !in book.chapters.indices) return@LaunchedEffect
        delay(latestReaderSettings.value.ttsChapterPauseMs.toLong())
        val engine = tts ?: run {
            speaking = false
            narrationAutoAdvanceTarget = -1
            return@LaunchedEffect
        }
        var target = firstTarget
        var nextText = ""
        while (target in book.chapters.indices && nextText.isBlank()) {
            val file = File(book.contentPath, book.chapters[target].href)
            nextText = runCatching {
                EpubParser.htmlToText(EpubParser.readDocument(file))
            }.getOrDefault("")
            if (nextText.isBlank()) target++
        }
        if (target !in book.chapters.indices || nextText.isBlank()) {
            narrationAutoAdvanceTarget = -1
            speaking = false
            narrationPaused = false
            return@LaunchedEffect
        }
        changeChapter(target)
        narrationChapter = target
        narrationChunk = 0
        narrationOffset = 0
        narrationEndOffset = 0
        narrationAutoAdvanceTarget = -1
        narrationStartWhenPageReady = true
        speaking = true
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { scaffoldPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(scaffoldPadding)
        ) {
            EpubWebView(
                book = book,
                chapterIndex = chapterIndex,
                settings = settings,
                initialProgress = chapterProgress,
                onWebViewReady = { webView = it },
                onPageReady = {
                    val engine = tts
                    if (narrationStartWhenPageReady && engine != null && ttsReady) {
                        narrationStartWhenPageReady = false
                        beginNarration(engine, startFromProgress = false)
                    } else if (
                        (speaking || narrationPaused) && narrationChapter == chapterIndex
                    ) {
                        narrationChunks.getOrNull(narrationChunk)?.let { chunk ->
                            webView?.showNarrationPosition(
                                blockIndex = chunk.blockIndex,
                                startOffset = chunk.domOffset(narrationOffset),
                                endOffset = chunk.endOffset,
                                follow = true
                            )
                            lastNarrationBlock = chunk.blockIndex
                        }
                    }
                },
                onSelectionChanged = { pendingSelection = it },
                onTap = { controlsVisible = !controlsVisible },
                onProgress = { progress ->
                    if (abs(progress - chapterProgress) >= .002f || progress >= .999f) {
                        chapterProgress = progress
                    }
                },
                onPreviousChapter = { changeChapter(chapterIndex - 1, 1f) },
                onNextChapter = { changeChapter(chapterIndex + 1) },
                annotations = annotations.filter { it.chapterIndex == chapterIndex },
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        if (settings.extraDim > 0f) {
                            drawRect(Color.Black.copy(alpha = settings.extraDim.coerceIn(0f, .8f)))
                        }
                    }
                    .testTag("epub_content")
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderTopBar(
                    title = chapter.title,
                    colors = colors,
                    onBack = onBack,
                    onContents = { showContents = true },
                    onSettings = { showSettings = true }
                )
            }

            if (!controlsVisible) {
                LinearProgressIndicator(
                    progress = {
                        ((chapterIndex + chapterProgress) / book.chapters.size).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                    color = colors.muted,
                    trackColor = Color.Transparent
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReaderBottomBar(
                    book = book,
                    chapterIndex = chapterIndex,
                    chapterProgress = chapterProgress,
                    colors = colors,
                    speaking = speaking,
                    onPrevious = {
                        if (settings.readingMode == ReadingMode.PAGED) {
                            if (webView?.movePage(-1) != true) {
                                changeChapter(chapterIndex - 1, 1f)
                            }
                        } else if (chapterProgress > .08f) {
                            webView?.scrollTo(0, 0)
                        } else {
                            changeChapter(chapterIndex - 1, 1f)
                        }
                    },
                    onNext = {
                        if (
                            settings.readingMode != ReadingMode.PAGED ||
                            webView?.movePage(1) != true
                        ) {
                            changeChapter(chapterIndex + 1)
                        }
                    },
                    onNarrate = { startNarration() },
                    onSettings = { showSettings = true },
                    onAnnotate = {
                        val cachedSelection = pendingSelection
                        if (cachedSelection != null) {
                            annotationDraft = AnnotationDraft(
                                selectedText = cachedSelection.text,
                                blockIndex = cachedSelection.blockIndex,
                                startOffset = cachedSelection.startOffset,
                                endOffset = cachedSelection.endOffset,
                                endBlockIndex = cachedSelection.endBlockIndex
                            )
                        } else webView?.captureSelection { selection ->
                            if (selection == null) {
                                showAnnotations = true
                            } else {
                                annotationDraft = AnnotationDraft(
                                    selectedText = selection.text,
                                    blockIndex = selection.blockIndex,
                                    startOffset = selection.startOffset,
                                    endOffset = selection.endOffset,
                                    endBlockIndex = selection.endBlockIndex
                                )
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = pendingSelection != null && annotationDraft == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = if (controlsVisible) 138.dp else 24.dp
                    )
            ) {
                Surface(
                    color = colors.surface.copy(alpha = .98f),
                    contentColor = colors.foreground,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    pendingSelection?.let { selection ->
                                        startNarration(selection)
                                        webView?.clearTextSelection()
                                        pendingSelection = null
                                        controlsVisible = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = ReaderAccent, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Leer desde aquí", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Box(Modifier.width(1.dp).height(28.dp).background(colors.muted.copy(alpha = .35f)))
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    pendingSelection?.let { selection ->
                                        annotationDraft = AnnotationDraft(
                                            selectedText = selection.text,
                                            blockIndex = selection.blockIndex,
                                            startOffset = selection.startOffset,
                                            endOffset = selection.endOffset,
                                            endBlockIndex = selection.endBlockIndex
                                        )
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Highlight, null, tint = ReaderAccent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Subrayar", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showContents) {
        ContentsSheet(
            book = book,
            currentChapter = chapterIndex,
            onDismiss = { showContents = false },
            onSelect = {
                changeChapter(it)
                showContents = false
            }
        )
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            onDismiss = { showSettings = false },
            compactPreview = true,
            isBookOverride = hasCustomSettings,
            onUseGlobalSettings = {
                onUseGlobalSettings()
                showSettings = false
            },
            onBrightnessChanged = onGlobalBrightnessChanged,
            onExtraDimChanged = onGlobalExtraDimChanged
        )
    }

    if (showAnnotations) {
        AnnotationsSheet(
            annotations = annotations.filter { it.chapterIndex == chapterIndex },
            onDismiss = { showAnnotations = false },
            onAddChapterComment = {
                showAnnotations = false
                annotationDraft = AnnotationDraft("", -1, 0, 0)
            },
            onSelect = {
                webView?.scrollToAnnotation(it.id)
                showAnnotations = false
            },
            onEdit = {
                showAnnotations = false
                annotationDraft = AnnotationDraft(
                    selectedText = it.selectedText,
                    blockIndex = it.blockIndex,
                    startOffset = it.startOffset,
                    endOffset = it.endOffset,
                    endBlockIndex = it.endBlockIndex,
                    annotationId = it.id,
                    initialNote = it.note,
                    initialColorIndex = it.colorIndex,
                    createdAt = it.createdAt
                )
            },
            onDelete = onDeleteAnnotation
        )
    }

    annotationDraft?.let { draft ->
        AnnotationEditorDialog(
            draft = draft,
            onDismiss = { annotationDraft = null },
            onSave = { note, colorIndex ->
                val savedAnnotation = ReaderAnnotation(
                    id = draft.annotationId ?: java.util.UUID.randomUUID().toString(),
                    chapterIndex = chapterIndex,
                    selectedText = draft.selectedText,
                    note = note,
                    blockIndex = draft.blockIndex,
                    startOffset = draft.startOffset,
                    endOffset = draft.endOffset,
                    endBlockIndex = draft.endBlockIndex,
                    colorIndex = colorIndex,
                    createdAt = draft.createdAt
                )
                onSaveAnnotation(savedAnnotation)
                webView?.let { readerView ->
                    readerView.clearTextSelection()
                    readerView.annotationsJson = annotationsToJson(
                        annotations.filterNot { it.id == savedAnnotation.id } + savedAnnotation
                    )
                    readerView.applyAnnotations(force = true)
                }
                pendingSelection = null
                annotationDraft = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnotationsSheet(
    annotations: List<ReaderAnnotation>,
    onDismiss: () -> Unit,
    onAddChapterComment: () -> Unit,
    onSelect: (ReaderAnnotation) -> Unit,
    onEdit: (ReaderAnnotation) -> Unit,
    onDelete: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notes, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Anotaciones", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    Text("Subrayados y comentarios de este capítulo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAddChapterComment) {
                    Icon(Icons.Default.AddComment, "Comentar capítulo")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (annotations.isEmpty()) {
                Text(
                    "Selecciona una frase y toca el lápiz para crear un subrayado. También puedes dejar una nota general del capítulo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 22.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    itemsIndexed(annotations, key = { _, item -> item.id }) { _, annotation ->
                        Card(
                            onClick = { onSelect(annotation) },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    if (annotation.selectedText.isBlank()) Icons.Default.Notes else Icons.Default.Highlight,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    if (annotation.selectedText.isNotBlank()) {
                                        Text(
                                            "“${annotation.selectedText}”",
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 3
                                        )
                                    } else {
                                        Text("Comentario del capítulo", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    }
                                    if (annotation.note.isNotBlank()) {
                                        Spacer(Modifier.height(5.dp))
                                        Text(annotation.note, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                                    }
                                }
                                IconButton(onClick = { onEdit(annotation) }) {
                                    Icon(Icons.Default.Edit, "Editar anotación")
                                }
                                IconButton(onClick = { onDelete(annotation.id) }) {
                                    Icon(Icons.Default.DeleteOutline, "Eliminar anotación")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun AnnotationEditorDialog(
    draft: AnnotationDraft,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var note by remember(draft) { mutableStateOf(draft.initialNote) }
    var colorIndex by remember(draft) { mutableIntStateOf(draft.initialColorIndex) }
    val canSave = draft.selectedText.isNotBlank() || note.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (draft.selectedText.isBlank()) Icons.Default.Notes else Icons.Default.Highlight, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                when {
                    draft.annotationId != null -> "Editar anotación"
                    draft.selectedText.isBlank() -> "Comentario del capítulo"
                    else -> "Subrayar y comentar"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (draft.selectedText.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text("“${draft.selectedText}”", modifier = Modifier.padding(14.dp), maxLines = 5)
                    }
                    Text("Color del subrayado", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("Amarillo", "Verde", "Lavanda", "Rosa").forEachIndexed { index, label ->
                            FilterChip(selected = colorIndex == index, onClick = { colorIndex = index }, label = { Text(label) })
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Comentario (opcional para un subrayado)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note.trim(), colorIndex) }, enabled = canSave) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EpubWebView(
    book: Book,
    chapterIndex: Int,
    settings: ReaderSettings,
    initialProgress: Float,
    onWebViewReady: (ReaderWebView) -> Unit,
    onPageReady: () -> Unit,
    onSelectionChanged: (SelectionData?) -> Unit,
    onTap: () -> Unit,
    onProgress: (Float) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    annotations: List<ReaderAnnotation>,
    modifier: Modifier = Modifier
) {
    val colors = readingColors(settings.theme)
    val latestTap = rememberUpdatedState(onTap)
    val latestProgress = rememberUpdatedState(onProgress)
    val latestPreviousChapter = rememberUpdatedState(onPreviousChapter)
    val latestNextChapter = rememberUpdatedState(onNextChapter)
    val latestSelection = rememberUpdatedState(onSelectionChanged)
    val latestPageReady = rememberUpdatedState(onPageReady)
    val annotationsJson = remember(annotations) { annotationsToJson(annotations) }
    val signature = buildString {
        append(book.id).append(':').append(chapterIndex).append(':')
        append(settings.theme).append(':').append(settings.font).append(':')
        append(settings.alignment).append(':')
        append(settings.readingMode).append(':')
        append(settings.fontSize).append(':').append(settings.lineHeight).append(':')
        append(settings.horizontalMargin)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ReaderWebView(context).apply {
                setBackgroundColor(colors.background.toArgbCompat())
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                this.settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = true
                    allowContentAccess = false
                    blockNetworkLoads = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = false
                    useWideViewPort = true
                    defaultTextEncodingName = "UTF-8"
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true

                    override fun onPageFinished(view: WebView, url: String?) {
                        (view as? ReaderWebView)?.apply {
                            restoreProgress()
                            postDelayed({ applyAnnotations(force = true) }, 180)
                            postDelayed({
                                latestPageReady.value()
                            }, 220)
                        }
                    }
                }
                var downX = 0f
                var downY = 0f
                var startedAtTop = false
                var startedAtBottom = false
                var multiTouch = false
                val swipeThreshold = 56f * resources.displayMetrics.density
                val scaleDetector = ScaleGestureDetector(
                    context,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            zoomBy(detector.scaleFactor.coerceIn(.8f, 1.25f))
                            return true
                        }
                    }
                )
                setOnTouchListener { touchedView, event ->
                    if (readingMode == ReadingMode.PAGED) scaleDetector.onTouchEvent(event)
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            startedAtTop = !canScrollVertically(-1)
                            startedAtBottom = !canScrollVertically(1)
                            multiTouch = false
                        }
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            multiTouch = true
                        }
                        MotionEvent.ACTION_UP -> {
                            val deltaX = event.x - downX
                            val deltaY = event.y - downY
                            if (multiTouch) {
                                multiTouch = false
                            } else if (
                                readingMode == ReadingMode.PAGED &&
                                abs(deltaX) > swipeThreshold &&
                                abs(deltaX) > abs(deltaY)
                            ) {
                                val direction = if (deltaX < 0) 1 else -1
                                if (!movePage(direction)) {
                                    if (direction > 0) latestNextChapter.value()
                                    else latestPreviousChapter.value()
                                }
                            } else if (
                                readingMode == ReadingMode.SCROLL &&
                                abs(deltaY) > swipeThreshold &&
                                abs(deltaY) > abs(deltaX)
                            ) {
                                if (deltaY < 0 && startedAtBottom) latestNextChapter.value()
                                else if (deltaY > 0 && startedAtTop) latestPreviousChapter.value()
                            } else if (abs(deltaX) < 14 && abs(deltaY) < 14) {
                                touchedView.performClick()
                                latestTap.value()
                            }
                        }
                    }
                    // In paged mode WebView's own horizontal gesture handling is
                    // inconsistent across Android System WebView versions. Consume
                    // the gesture here and advance exactly one page on ACTION_UP.
                    // Scroll mode keeps WebView's native pinch-to-zoom. Paged mode
                    // is consumed here because its horizontal gesture is deterministic.
                    readingMode == ReadingMode.PAGED
                }
                setOnScrollChangeListener { view, _, _, _, _ ->
                    latestProgress.value((view as ReaderWebView).currentProgress())
                }
                reportProgress = { latestProgress.value(it) }
                reportSelection = { latestSelection.value(it) }
                onWebViewReady(this)
            }
        },
        update = { view ->
            if (view.tag != signature) {
                view.tag = signature
                view.readingMode = settings.readingMode
                view.pendingProgress = initialProgress
                view.annotationsJson = annotationsJson
                view.reportProgress = { latestProgress.value(it) }
                view.reportSelection = { latestSelection.value(it) }
                view.setBackgroundColor(colors.background.toArgbCompat())
                val chapter = book.chapters[chapterIndex]
                val file = File(book.contentPath, chapter.href)
                val original = runCatching { EpubParser.readDocument(file) }.getOrElse {
                    "<html><body><h2>No se pudo abrir este capítulo</h2><p>${it.message.orEmpty()}</p></body></html>"
                }
                val html = injectReaderCss(original, settings, colors)
                val baseUrl = file.parentFile?.toURI()?.toString() ?: File(book.contentPath).toURI().toString()
                view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            } else if (view.annotationsJson != annotationsJson) {
                view.annotationsJson = annotationsJson
                view.applyAnnotations()
            }
            view.reportSelection = { latestSelection.value(it) }
        }
    )
}

@Composable
private fun ReaderTopBar(
    title: String,
    colors: ReadingColors,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = onBack,
            color = colors.surface.copy(alpha = .94f),
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = colors.foreground)
            }
        }
        Surface(
            color = colors.surface.copy(alpha = .9f),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 5.dp,
            modifier = Modifier.weight(1f).height(46.dp)
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(ReaderCoral))
                Spacer(Modifier.width(9.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.foreground,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Surface(
            color = colors.surface.copy(alpha = .94f),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 6.dp
        ) {
            Row {
                IconButton(onClick = onContents, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, "Índice", tint = colors.foreground)
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Default.Settings, "Ajustes", tint = colors.foreground)
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    book: Book,
    chapterIndex: Int,
    chapterProgress: Float,
    colors: ReadingColors,
    speaking: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNarrate: () -> Unit,
    onSettings: () -> Unit,
    onAnnotate: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        val compact = maxWidth < 380.dp
        Surface(
            color = ReaderInk.copy(alpha = .96f),
            shadowElevation = 16.dp,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { chapterProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = ReaderCoral,
                    trackColor = Color.White.copy(alpha = .1f)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = .1f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${(chapterProgress * 100).roundToInt()}%", fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                                Text(
                                    "${chapterIndex + 1}/${book.chapters.size}",
                                    color = Color.White.copy(alpha = .55f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    IconButton(onClick = onPrevious, enabled = chapterIndex > 0 || chapterProgress > .01f) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior", tint = Color.White)
                    }
                    FilledIconButton(
                        onClick = onNarrate,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (speaking) ReaderCoral else ReaderAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(if (compact) 46.dp else 52.dp)
                    ) {
                        Icon(
                            if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (speaking) "Pausar narración" else "Narrar o reanudar",
                            modifier = Modifier.size(if (compact) 24.dp else 27.dp)
                        )
                    }
                    IconButton(onClick = onNext, enabled = chapterIndex < book.chapters.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Siguiente", tint = Color.White)
                    }
                    if (!compact) {
                        IconButton(onClick = onAnnotate) {
                            Icon(Icons.Default.BorderColor, "Anotaciones", tint = Color.White)
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.FormatSize, "Apariencia", tint = Color.White)
                        }
                    }
                }
                if (compact) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 7.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onAnnotate, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Default.BorderColor, "Anotaciones", tint = Color.White)
                        }
                        IconButton(onClick = onSettings, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Default.FormatSize, "Apariencia", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentsSheet(
    book: Book,
    currentChapter: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Contenido", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${book.chapters.size} capítulos",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(book.chapters) { index, chapter ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .background(
                            if (index == currentChapter) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        (index + 1).toString().padStart(2, '0'),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.size(18.dp))
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (index == currentChapter) {
                        Icon(Icons.Default.Check, "Actual", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (index != book.chapters.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSettingsSheet(
    settings: ReaderSettings,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text("Apariencia", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Haz que cada libro se sienta tuyo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                SettingLabel("Tema")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 22.dp)
                ) {
                    items(ReaderTheme.entries) { theme ->
                        val preview = readingColors(theme)
                        ThemeCard(
                            theme.label,
                            theme,
                            settings.theme,
                            preview.background,
                            preview.foreground,
                            Modifier.width(104.dp)
                        ) { onSettingsChanged(settings.copy(theme = it)) }
                    }
                }
            }
            item {
                SettingLabel("Tipografía")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 22.dp)
                ) {
                    items(ReaderFont.entries.size) { index ->
                        val font = ReaderFont.entries[index]
                        FilterChip(
                            selected = settings.font == font,
                            onClick = { onSettingsChanged(settings.copy(font = font)) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Aa",
                                        fontFamily = font.previewFamily(),
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
                            onClick = {
                                onSettingsChanged(settings.copy(alignment = alignment))
                            },
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
                ) { onSettingsChanged(settings.copy(fontSize = it.roundToInt())) }
            }
            item {
                SettingSlider(
                    label = "Interlineado",
                    valueLabel = String.format(Locale.getDefault(), "%.1f", settings.lineHeight),
                    value = settings.lineHeight,
                    range = 1.2f..2.2f,
                    steps = 9
                ) { onSettingsChanged(settings.copy(lineHeight = it)) }
            }
            item {
                SettingSlider(
                    label = "Márgenes",
                    valueLabel = "${settings.horizontalMargin}",
                    value = settings.horizontalMargin.toFloat(),
                    range = 8f..48f,
                    steps = 19
                ) { onSettingsChanged(settings.copy(horizontalMargin = it.roundToInt())) }
            }
            item {
                SettingSlider(
                    label = "Brillo",
                    valueLabel = "${(settings.brightness * 100).roundToInt()}%",
                    value = settings.brightness,
                    range = .35f..1f,
                    steps = 12
                ) { onSettingsChanged(settings.copy(brightness = it)) }
            }
            item {
                SettingSlider(
                    label = "Velocidad de voz",
                    valueLabel = "${String.format(Locale.getDefault(), "%.1f", settings.ttsRate)}×",
                    value = settings.ttsRate,
                    range = .5f..2f,
                    steps = 14
                ) { onSettingsChanged(settings.copy(ttsRate = it)) }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(keepScreenOn = it))
                        }
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ThemeCard(
    label: String,
    theme: ReaderTheme,
    selected: ReaderTheme,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    onClick: (ReaderTheme) -> Unit
) {
    Card(
        onClick = { onClick(theme) },
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = if (theme == selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            Text("Aa", color = foreground, style = MaterialTheme.typography.titleLarge)
            Text(label, color = foreground, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(valueLabel, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
}

private fun readingColors(theme: ReaderTheme): ReadingColors = when (theme) {
    ReaderTheme.PAPER -> ReadingColors(
        background = Color(0xFFFAF9F5),
        foreground = Color(0xFF242522),
        muted = Color(0xFF6C706A),
        surface = Color(0xFFFDFCF8),
        cssBackground = "#FAF9F5",
        cssForeground = "#242522"
    )
    ReaderTheme.SEPIA -> ReadingColors(
        background = Color(0xFFF3E6C8),
        foreground = Color(0xFF443827),
        muted = Color(0xFF7A6A50),
        surface = Color(0xFFF7ECCC),
        cssBackground = "#F3E6C8",
        cssForeground = "#443827"
    )
    ReaderTheme.SAND -> ReadingColors(
        background = Color(0xFFE8D6B5),
        foreground = Color(0xFF3D3326),
        muted = Color(0xFF786750),
        surface = Color(0xFFF0DFC0),
        cssBackground = "#E8D6B5",
        cssForeground = "#3D3326"
    )
    ReaderTheme.MINT -> ReadingColors(
        background = Color(0xFFE3F0E7),
        foreground = Color(0xFF233C30),
        muted = Color(0xFF64776D),
        surface = Color(0xFFEDF6F0),
        cssBackground = "#E3F0E7",
        cssForeground = "#233C30"
    )
    ReaderTheme.LAVENDER -> ReadingColors(
        background = Color(0xFFEDE9FF),
        foreground = Color(0xFF302A4A),
        muted = Color(0xFF716A8C),
        surface = Color(0xFFF5F2FF),
        cssBackground = "#EDE9FF",
        cssForeground = "#302A4A"
    )
    ReaderTheme.ROSE -> ReadingColors(
        background = Color(0xFFFFE9EC),
        foreground = Color(0xFF4A2931),
        muted = Color(0xFF906B73),
        surface = Color(0xFFFFF3F4),
        cssBackground = "#FFE9EC",
        cssForeground = "#4A2931"
    )
    ReaderTheme.SKY -> ReadingColors(
        background = Color(0xFFE5F3FA),
        foreground = Color(0xFF203744),
        muted = Color(0xFF63808D),
        surface = Color(0xFFF1FAFE),
        cssBackground = "#E5F3FA",
        cssForeground = "#203744"
    )
    ReaderTheme.SLATE -> ReadingColors(
        background = Color(0xFF26323A),
        foreground = Color(0xFFDDE7EC),
        muted = Color(0xFF9BAAB2),
        surface = Color(0xFF303E47),
        cssBackground = "#26323A",
        cssForeground = "#DDE7EC"
    )
    ReaderTheme.NIGHT -> ReadingColors(
        background = Color(0xFF171A18),
        foreground = Color(0xFFD5D8D1),
        muted = Color(0xFF929B93),
        surface = Color(0xFF1E2320),
        cssBackground = "#171A18",
        cssForeground = "#D5D8D1"
    )
    ReaderTheme.OLED -> ReadingColors(
        background = Color.Black,
        foreground = Color(0xFFE7E7E7),
        muted = Color(0xFF929292),
        surface = Color(0xFF0C0C0C),
        cssBackground = "#000000",
        cssForeground = "#E7E7E7"
    )
}

private fun injectReaderCss(
    html: String,
    settings: ReaderSettings,
    colors: ReadingColors
): String {
    // A number of fixed-layout EPUBs explicitly disable zoom. Luma owns the
    // viewport while reading so illustrations can always be enlarged.
    val document = html
        .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
        .replace(Regex("(?is)<meta\\b[^>]*\\bname\\s*=\\s*['\"]viewport['\"][^>]*?/?>"), "")
    val pagedCss = if (settings.readingMode == ReadingMode.PAGED) {
        """
          html {
            width: 100% !important;
            height: 100% !important;
            overflow-x: auto !important;
            overflow-y: hidden !important;
          }
          body {
            box-sizing: border-box !important;
            width: 100vw !important;
            max-width: 100vw !important;
            height: 100vh !important;
            max-height: 100vh !important;
            overflow: visible !important;
            column-width: calc(100vw - ${settings.horizontalMargin * 2}px) !important;
            -webkit-column-width: calc(100vw - ${settings.horizontalMargin * 2}px) !important;
            column-gap: ${settings.horizontalMargin * 2}px !important;
            -webkit-column-gap: ${settings.horizontalMargin * 2}px !important;
            column-fill: auto !important;
          }
          body > * {
            max-width: none !important;
            break-inside: avoid;
          }
        """.trimIndent()
    } else {
        """
          html { overflow-x: hidden !important; overflow-y: auto !important; }
          body > * {
            max-width: 760px;
            margin-left: auto !important;
            margin-right: auto !important;
          }
        """.trimIndent()
    }
    val darkColorScheme = settings.theme in setOf(
        ReaderTheme.NIGHT, ReaderTheme.SLATE, ReaderTheme.OLED
    )
    val css = """
        <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=0.75, maximum-scale=5.0, user-scalable=yes" />
        <style id="luma-reader-style">
          :root { color-scheme: ${if (darkColorScheme) "dark" else "light"}; }
          html, body {
            background: ${colors.cssBackground} !important;
            color: ${colors.cssForeground} !important;
          }
          body {
            box-sizing: border-box !important;
            margin: 0 !important;
            padding: 120px ${settings.horizontalMargin}px 150px !important;
            font-family: ${settings.font.css} !important;
            font-size: ${settings.fontSize}px !important;
            line-height: ${settings.lineHeight} !important;
            overflow-wrap: break-word;
            word-break: normal;
            text-rendering: optimizeLegibility;
            text-align: ${settings.alignment.css} !important;
            hyphens: auto;
            word-spacing: normal !important;
            letter-spacing: normal !important;
          }
          body, body * {
            color: ${colors.cssForeground} !important;
            font-family: ${settings.font.css} !important;
            line-height: ${settings.lineHeight} !important;
            word-spacing: normal !important;
            letter-spacing: normal !important;
            text-align: ${settings.alignment.css} !important;
          }
          body *::before, body *::after {
            color: ${colors.cssForeground} !important;
          }
          p { margin: 0 0 1em !important; }
          h1, h2, h3, h4, h5, h6 {
            color: ${colors.cssForeground} !important;
            font-family: ${settings.font.css} !important;
            line-height: 1.25 !important;
          }
          img, svg, video { max-width: 100% !important; height: auto !important; }
          table { max-width: 100% !important; }
          a { color: ${colors.cssForeground} !important; text-decoration-color: currentColor; }
          ::selection { background: rgba(101, 88, 245, .28); }
          ::highlight(luma-tts) {
            background: rgba(101, 88, 245, .34);
            text-decoration: underline 2px rgba(255, 113, 91, .95);
          }
          .luma-tts-active {
            background: rgba(101, 88, 245, .10) !important;
            border-radius: .4em;
            box-shadow: -5px 0 0 rgba(255, 113, 91, .88);
            transition: background-color 180ms ease, box-shadow 180ms ease;
          }
          pre, code { white-space: pre-wrap !important; }
          $pagedCss
        </style>
    """.trimIndent()
    val annotationScript = """
        <script>
          window.LumaAnnotations = (function() {
            var blockSelector = 'p,li,blockquote,h1,h2,h3,h4,h5,h6,td,pre,figcaption';
            function blockFor(node) {
              var element = node && (node.nodeType === 3 ? node.parentElement : node);
              return element && (element.closest(blockSelector) || document.body);
            }
            function offsetInBlock(block, container, offset) {
              var range = document.createRange();
              range.selectNodeContents(block);
              range.setEnd(container, offset);
              return range.toString().length;
            }
            function rangeForOffsets(block, start, end) {
              var walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT);
              var node, cursor = 0, startNode = null, endNode = null, startAt = 0, endAt = 0;
              while (node = walker.nextNode()) {
                var next = cursor + node.nodeValue.length;
                if (!startNode && start >= cursor && start <= next) { startNode = node; startAt = start - cursor; }
                if (!endNode && end >= cursor && end <= next) { endNode = node; endAt = end - cursor; break; }
                cursor = next;
              }
              if (!startNode || !endNode) return null;
              var range = document.createRange();
              range.setStart(startNode, startAt); range.setEnd(endNode, endAt);
              return range;
            }
            function markOffsets(block, start, end, item, color) {
              var walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT);
              var node, cursor = 0, segments = [];
              while (node = walker.nextNode()) {
                var length = node.nodeValue.length;
                var from = Math.max(start, cursor) - cursor;
                var to = Math.min(end, cursor + length) - cursor;
                if (to > from) segments.push({node: node, from: from, to: to});
                cursor += length;
                if (cursor >= end) break;
              }
              var marked = 0;
              for (var i = segments.length - 1; i >= 0; i--) {
                var segment = segments[i];
                var selectedNode = segment.node;
                if (segment.to < selectedNode.nodeValue.length) selectedNode.splitText(segment.to);
                if (segment.from > 0) selectedNode = selectedNode.splitText(segment.from);
                var parent = selectedNode.parentNode;
                if (!parent) continue;
                var mark = document.createElement('mark');
                mark.setAttribute('data-luma-id', item.id || '');
                mark.style.backgroundColor = color;
                mark.style.color = 'inherit';
                parent.replaceChild(mark, selectedNode);
                mark.appendChild(selectedNode);
                marked++;
              }
              return marked;
            }
            function clearMarks() {
              Array.prototype.slice.call(document.querySelectorAll('mark[data-luma-id]')).forEach(function(mark) {
                var parent = mark.parentNode;
                if (!parent) return;
                parent.replaceChild(document.createTextNode(mark.textContent || ''), mark);
                parent.normalize();
              });
            }
            return {
              captureSelection: function() {
                var selection = window.getSelection();
                if (!selection || !selection.rangeCount || !selection.toString().trim()) return '';
                var range = selection.getRangeAt(0);
                var block = blockFor(range.startContainer);
                var endBlock = blockFor(range.endContainer);
                if (!block || !endBlock) return '';
                var blocks = Array.prototype.slice.call(document.querySelectorAll(blockSelector));
                var blockIndex = blocks.indexOf(block);
                var endBlockIndex = blocks.indexOf(endBlock);
                if (blockIndex < 0 || endBlockIndex < blockIndex) return '';
                var text = selection.toString().trim();
                var start = offsetInBlock(block, range.startContainer, range.startOffset);
                var end = offsetInBlock(endBlock, range.endContainer, range.endOffset);
                return JSON.stringify({
                  text:text,
                  blockIndex:blockIndex,
                  endBlockIndex:endBlockIndex,
                  startOffset:start,
                  endOffset:end
                });
              },
              apply: function(payload) {
                var activeSelection = window.getSelection && window.getSelection();
                if (activeSelection) activeSelection.removeAllRanges();
                clearMarks();
                var items = [];
                try { items = JSON.parse(payload || '[]'); } catch (e) { return; }
                var blocks = Array.prototype.slice.call(document.querySelectorAll(blockSelector));
                var colors = ['#FFE38A','#B7F0D4','#D9C7FF','#FFB8C7'];
                items.sort(function(a,b) {
                  var blockDelta = (b.blockIndex || 0) - (a.blockIndex || 0);
                  return blockDelta || ((b.startOffset || 0) - (a.startOffset || 0));
                });
                var marked = 0;
                items.forEach(function(item) {
                  var first = item.blockIndex || 0;
                  var last = item.endBlockIndex == null ? first : item.endBlockIndex;
                  for (var blockIndex = last; blockIndex >= first; blockIndex--) {
                    var block = blocks[blockIndex];
                    if (!block) continue;
                    var start = blockIndex === first ? (item.startOffset || 0) : 0;
                    var end = blockIndex === last ? (item.endOffset || 0) : block.textContent.length;
                    if (end <= start) continue;
                    marked += markOffsets(
                      block,
                      start,
                      end,
                      item,
                      colors[item.colorIndex || 0] || colors[0]
                    );
                  }
                });
                return marked;
              },
              scrollTo: function(id) {
                var marks = document.querySelectorAll('mark[data-luma-id]');
                for (var i = 0; i < marks.length; i++) if (marks[i].getAttribute('data-luma-id') === id) {
                  marks[i].scrollIntoView({behavior:'smooth', block:'center'}); break;
                }
              }
            };
          })();
          window.LumaNarration = (function() {
            var blockSelector = 'p,li,blockquote,h1,h2,h3,h4,h5,h6,td,pre,figcaption';
            var activeBlock = null;
            function allBlocks() {
              return Array.prototype.slice.call(document.querySelectorAll(blockSelector));
            }
            function clear() {
              if (activeBlock) activeBlock.classList.remove('luma-tts-active');
              activeBlock = null;
              if (window.CSS && CSS.highlights) CSS.highlights.delete('luma-tts');
            }
            function rangeForOffsets(block, start, end) {
              var walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT);
              var node, cursor = 0, startNode = null, endNode = null, startAt = 0, endAt = 0;
              while (node = walker.nextNode()) {
                var next = cursor + node.nodeValue.length;
                if (!startNode && start >= cursor && start <= next) {
                  startNode = node; startAt = start - cursor;
                }
                if (!endNode && end >= cursor && end <= next) {
                  endNode = node; endAt = end - cursor; break;
                }
                cursor = next;
              }
              if (!startNode || !endNode) return null;
              var range = document.createRange();
              range.setStart(startNode, startAt);
              range.setEnd(endNode, endAt);
              return range;
            }
            return {
              blocks: function() {
                return JSON.stringify(allBlocks().map(function(block, index) {
                  return { blockIndex: index, text: block.textContent || '' };
                }).filter(function(item) { return item.text.trim().length > 0; }));
              },
              clear: clear,
              highlight: function(blockIndex, startOffset, endOffset, follow) {
                var block = allBlocks()[blockIndex];
                if (!block) return false;
                if (activeBlock !== block) {
                  if (activeBlock) activeBlock.classList.remove('luma-tts-active');
                  activeBlock = block;
                  activeBlock.classList.add('luma-tts-active');
                }
                if (window.CSS && CSS.highlights) CSS.highlights.delete('luma-tts');
                var safeStart = Math.max(0, Math.min(startOffset || 0, block.textContent.length));
                var safeEnd = Math.max(safeStart, Math.min(endOffset || safeStart, block.textContent.length));
                var range = rangeForOffsets(block, safeStart, safeEnd);
                if (range && !range.collapsed && window.CSS && CSS.highlights && window.Highlight) {
                  CSS.highlights.set('luma-tts', new Highlight(range));
                }
                if (follow) {
                  var rect = block.getBoundingClientRect();
                  var comfortTop = window.innerHeight * .20;
                  var comfortBottom = window.innerHeight * .78;
                  if (rect.top < comfortTop || rect.bottom > comfortBottom) {
                    block.scrollIntoView({behavior:'smooth', block:'center', inline:'center'});
                  }
                }
                return true;
              }
            };
          })();
          (function() {
            var selectionTimer = null;
            function reportSelection() {
              if (!window.LumaSelection || !window.LumaAnnotations) return;
              window.LumaSelection.onSelection(window.LumaAnnotations.captureSelection());
            }
            document.addEventListener('selectionchange', function() {
              if (selectionTimer) clearTimeout(selectionTimer);
              selectionTimer = setTimeout(reportSelection, 100);
            });
            document.addEventListener('touchend', function() {
              setTimeout(reportSelection, 180);
            });
          })();
        </script>
    """.trimIndent()
    val head = Regex("(?i)<head[^>]*>")
    val match = head.find(document)
    return if (match != null) {
        document.replaceRange(match.range, match.value + css + annotationScript)
    } else {
        "<html><head>$css$annotationScript</head><body>$document</body></html>"
    }
}

private fun List<SpeechBlock>.chunkForSpeech(
    maxLength: Int = 460,
    sentencePauseMs: Int = 120,
    paragraphPauseMs: Int = 320
): List<SpeechChunk> {
    return buildList {
        this@chunkForSpeech.forEach { block ->
            val raw = block.text
            val sentenceRanges = Regex("[^.!?…]+(?:[.!?…]+|$)")
                .findAll(raw)
                .mapNotNull { match ->
                    var start = match.range.first
                    var end = match.range.last + 1
                    while (start < end && raw[start].isWhitespace()) start++
                    while (end > start && raw[end - 1].isWhitespace()) end--
                    if (start < end) start until end else null
                }
                .toList()
                .ifEmpty {
                    val start = raw.indexOfFirst { !it.isWhitespace() }
                    val end = raw.indexOfLast { !it.isWhitespace() } + 1
                    if (start >= 0 && end > start) listOf(start until end) else emptyList()
                }
            sentenceRanges.forEachIndexed { sentenceIndex, sentenceRange ->
                val parts = splitSpeechRange(raw, sentenceRange.first, sentenceRange.last + 1, maxLength)
                parts.forEachIndexed { partIndex, partRange ->
                    val normalized = normalizeSpeechRange(raw, partRange.first, partRange.last + 1)
                    if (normalized.text.isNotEmpty()) {
                        val isSentenceEnd = partIndex == parts.lastIndex
                        val isBlockEnd = isSentenceEnd && sentenceIndex == sentenceRanges.lastIndex
                        add(
                            SpeechChunk(
                                text = normalized.text,
                                pauseAfterMs = when {
                                    !isSentenceEnd -> 0
                                    isBlockEnd -> paragraphPauseMs.coerceAtLeast(0)
                                    else -> sentencePauseMs.coerceAtLeast(0)
                                },
                                blockIndex = block.blockIndex,
                                startOffset = partRange.first,
                                endOffset = partRange.last + 1,
                                sourceOffsets = normalized.sourceOffsets
                            )
                        )
                    }
                }
            }
        }
    }
}

private data class NormalizedSpeechRange(
    val text: String,
    val sourceOffsets: IntArray
)

private fun splitSpeechRange(
    source: String,
    start: Int,
    endExclusive: Int,
    maxLength: Int
): List<IntRange> = buildList {
    var cursor = start
    while (cursor < endExclusive) {
        while (cursor < endExclusive && source[cursor].isWhitespace()) cursor++
        if (cursor >= endExclusive) break
        val hardEnd = (cursor + maxLength).coerceAtMost(endExclusive)
        var partEnd = hardEnd
        if (hardEnd < endExclusive) {
            var candidate = hardEnd
            while (candidate > cursor && !source[candidate - 1].isWhitespace()) candidate--
            if (candidate > cursor) partEnd = candidate
        }
        var trimmedEnd = partEnd
        while (trimmedEnd > cursor && source[trimmedEnd - 1].isWhitespace()) trimmedEnd--
        if (trimmedEnd > cursor) add(cursor until trimmedEnd)
        cursor = partEnd.coerceAtLeast(cursor + 1)
    }
}

private fun normalizeSpeechRange(
    source: String,
    start: Int,
    endExclusive: Int
): NormalizedSpeechRange {
    val text = StringBuilder()
    val offsets = ArrayList<Int>(endExclusive - start)
    var whitespacePending = false
    var whitespaceOffset = start
    for (index in start until endExclusive) {
        val character = source[index]
        if (character.isWhitespace()) {
            if (text.isNotEmpty() && !whitespacePending) whitespaceOffset = index
            whitespacePending = text.isNotEmpty()
        } else {
            if (whitespacePending) {
                text.append(' ')
                offsets += whitespaceOffset
                whitespacePending = false
            }
            text.append(character)
            offsets += index
        }
    }
    return NormalizedSpeechRange(text.toString(), offsets.toIntArray())
}

private fun SpeechChunk.domOffset(speechOffset: Int): Int = when {
    speechOffset <= 0 -> startOffset
    speechOffset >= sourceOffsets.size -> endOffset
    else -> sourceOffsets[speechOffset]
}

private fun SpeechChunk.speechOffsetForDom(domOffset: Int): Int {
    val found = sourceOffsets.indexOfFirst { it >= domOffset }
    return if (found >= 0) found else text.length
}

private fun String?.narrationPosition(): Triple<Int, Int, Int>? {
    if (this == null || !startsWith("luma_")) return null
    val parts = split('_')
    if (parts.size < 5) return null
    val chapter = parts[1].toIntOrNull() ?: return null
    val chunk = parts[2].toIntOrNull() ?: return null
    val offset = parts[3].toIntOrNull() ?: return null
    return Triple(chapter, chunk, offset)
}

private fun String?.toSelectionData(): SelectionData? {
    if (this.isNullOrBlank()) return null
    return runCatching {
        val json = JSONObject(this)
        SelectionData(
            text = json.optString("text").trim(),
            blockIndex = json.optInt("blockIndex", -1),
            startOffset = json.optInt("startOffset"),
            endOffset = json.optInt("endOffset"),
            endBlockIndex = json.optInt("endBlockIndex", json.optInt("blockIndex", -1))
        )
    }.getOrNull()?.takeIf {
        it.text.isNotBlank() && it.blockIndex >= 0 && it.endBlockIndex >= it.blockIndex &&
            (it.endBlockIndex > it.blockIndex || it.endOffset > it.startOffset)
    }
}

private fun String?.toSpeechBlocks(): List<SpeechBlock> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(this)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("text")
                val blockIndex = item.optInt("blockIndex", -1)
                if (blockIndex >= 0 && text.isNotBlank()) add(SpeechBlock(blockIndex, text))
            }
        }
    }.getOrDefault(emptyList())
}

private fun annotationsToJson(annotations: List<ReaderAnnotation>): String =
    JSONArray().apply {
        annotations.forEach { annotation ->
            put(JSONObject().apply {
                put("id", annotation.id)
                put("blockIndex", annotation.blockIndex)
                put("endBlockIndex", annotation.endBlockIndex)
                put("startOffset", annotation.startOffset)
                put("endOffset", annotation.endOffset)
                put("colorIndex", annotation.colorIndex)
            })
        }
    }.toString()

private fun Color.toArgbCompat(): Int = AndroidColor.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)

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
