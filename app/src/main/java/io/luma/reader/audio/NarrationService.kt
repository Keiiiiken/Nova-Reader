package io.luma.reader.audio

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import io.luma.reader.MainActivity
import io.luma.reader.model.*
import io.luma.reader.reader.ChapterContent
import io.luma.reader.ui.SpeechBlock
import io.luma.reader.ui.SpeechChunk
import io.luma.reader.ui.chunkForSpeech
import io.luma.reader.ui.domOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jsoup.Jsoup
import java.util.Locale

internal data class NarrationState(val bookId: String? = null, val chapter: Int = 0, val block: Int = 0,
    val start: Int = 0, val end: Int = 0, val progress: Float = 0f, val playing: Boolean = false,
    val paused: Boolean = false, val sleepAt: Long = 0L, val error: String? = null)
internal data class NarrationRequest(val book: Book, val settings: ReaderSettings, val chapter: Int,
    val chunks: List<SpeechChunk>, val chunk: Int, val offset: Int)
internal object NarrationPlayer {
    val state = MutableStateFlow(NarrationState())
    @Volatile var pending: NarrationRequest? = null
    fun start(context: Context, request: NarrationRequest) {
        pending = request
        ContextCompat.startForegroundService(context, Intent(context, NarrationService::class.java).setAction("start"))
    }
    fun command(context: Context, action: String, minutes: Int = 0) {
        if (state.value.bookId != null) context.startService(Intent(context, NarrationService::class.java).setAction(action).putExtra("minutes", minutes))
    }
}

class NarrationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: TextToSpeech? = null
    private var request: NarrationRequest? = null
    private var chunks = emptyList<SpeechChunk>()
    private var chunk = 0
    private var offset = 0
    private var chapter = 0
    private var generation = 0
    private var sleepJob: Job? = null
    private var nextJob: Job? = null
    private var sleepAt = 0L
    private lateinit var session: MediaSessionCompat
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val noisy = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { pause() } }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("nova-narration", "Lectura en voz alta", NotificationManager.IMPORTANCE_LOW))
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { if (it < 0) pause() }.build()
        session = MediaSessionCompat(this, "Nova Reader").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resume() }; override fun onPause() { pause() }; override fun onStop() { stopSelf() }
                override fun onSkipToNext() { advanceChapter() }
                override fun onSkipToPrevious() { scope.launch { loadChapter((chapter - 1).coerceAtLeast(0)) } }
            })
            isActive = true
        }
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 29) startForeground(211, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(211, notification())
        when (intent?.action) {
            "start" -> NarrationPlayer.pending?.let { request -> NarrationPlayer.pending = null; begin(request) }
            "play" -> resume()
            "pause" -> pause()
            "stop" -> stopSelf()
            "next" -> advanceChapter()
            "timer" -> {
                val minutes = intent.getIntExtra("minutes", 0).coerceIn(0, 120)
                sleepJob?.cancel(); sleepAt = if (minutes == 0) 0 else System.currentTimeMillis() + minutes * 60_000L
                publish()
                if (minutes > 0) sleepJob = scope.launch { delay(minutes * 60_000L); pause(); sleepAt = 0; publish() }
            }
        }
        return START_NOT_STICKY
    }
    private fun begin(value: NarrationRequest) {
        generation++; nextJob?.cancel(); engine?.stop(); engine?.shutdown(); engine = null
        request = value; chaptersReset(value.chapter, value.chunks, value.chunk, value.offset)
        NarrationPlayer.state.value = NarrationState(bookId = value.book.id, chapter = chapter, paused = true)
        val init: (Int) -> Unit = { status -> scope.launch {
            if (status == TextToSpeech.SUCCESS) configureAndPlay() else fail("No se pudo iniciar la voz. Revisa el motor de voz instalado.")
        } }
        engine = if (value.settings.ttsEngine == null) TextToSpeech(applicationContext, init) else TextToSpeech(applicationContext, init, value.settings.ttsEngine)
    }
    private fun chaptersReset(index: Int, values: List<SpeechChunk>, startChunk: Int = 0, startOffset: Int = 0) {
        chapter = index; chunks = values; chunk = startChunk.coerceIn(0, values.lastIndex.coerceAtLeast(0)); offset = startOffset
    }
    private fun configureAndPlay() {
        val tts = engine ?: return; val settings = request?.settings ?: return
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        val voice = settings.ttsVoiceName?.let { name -> tts.voices?.firstOrNull { it.name == name } }
        if (voice != null) tts.voice = voice else {
            val result = tts.setLanguage(if (settings.ttsLanguageTag == "system") Locale.getDefault() else Locale.forLanguageTag(settings.ttsLanguageTag))
            if (result < TextToSpeech.LANG_AVAILABLE) { fail("Instala una voz para el idioma elegido en los ajustes de Android"); return }
        }
        tts.setSpeechRate(settings.ttsRate); tts.setPitch(settings.ttsPitch)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { scope.launch {
                if (id != "nova-$generation" || !NarrationPlayer.state.value.playing) return@launch
                val pause = chunks.getOrNull(chunk)?.pauseAfterMs ?: 0
                chunk++; offset = 0
                nextJob = scope.launch { delay(pause.toLong()); speakChunk() }
            } }
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { scope.launch {
                if (id == "nova-$generation" && NarrationPlayer.state.value.playing) {
                    val part = chunks.getOrNull(chunk) ?: return@launch
                    val base = utteranceOffset
                    NarrationPlayer.state.value = NarrationPlayer.state.value.copy(start = part.domOffset(base + start), end = part.domOffset(base + end))
                    offset = base + start
                }
            } }
            @Deprecated("Deprecated in Android") override fun onError(id: String?) { scope.launch { fail("La voz no pudo continuar. Comprueba los datos del motor de voz.") } }
        })
        resume()
    }
    private var utteranceOffset = 0
    private fun speakChunk() {
        if (!NarrationPlayer.state.value.playing) return
        val part = chunks.getOrNull(chunk)
        if (part == null) { advanceChapter(); return }
        offset = offset.coerceIn(0, part.text.length)
        utteranceOffset = offset
        generation++
        publish(playing = true)
        persist()
        val result = engine?.speak(part.text.substring(offset), TextToSpeech.QUEUE_FLUSH,
            Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, request?.settings?.ttsVolume ?: 1f) }, "nova-$generation")
        if (result == TextToSpeech.ERROR) fail("La voz no pudo leer este fragmento")
    }
    private fun resume() {
        if (engine == null || request == null) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { fail("Otra app está usando el audio. Inténtalo de nuevo."); return }
        publish(playing = true)
        speakChunk()
    }
    private fun pause() {
        if (request == null) return
        generation++; nextJob?.cancel(); publish(playing = false); engine?.stop(); persist()
        audio.abandonAudioFocusRequest(focus)
    }
    private fun advanceChapter() {
        val value = request ?: return
        if (!value.settings.ttsAutoAdvance || chapter >= value.book.chapters.lastIndex) { pause(); return }
        generation++; engine?.stop()
        nextJob?.cancel()
        nextJob = scope.launch { delay(value.settings.ttsChapterPauseMs.toLong()); loadChapter(chapter + 1) }
    }
    private suspend fun loadChapter(index: Int) {
        val value = request ?: return
        var next = index
        while (next in value.book.chapters.indices) {
            val parts = withContext(Dispatchers.IO) {
                val doc = Jsoup.parse(ChapterContent.read(value.book, next))
                val elements = doc.select("p,li,blockquote,h1,h2,h3,h4,h5,h6,td,pre,figcaption")
                val blocks = elements.mapIndexedNotNull { i, element ->
                    if (element.select("p,li,blockquote,h1,h2,h3,h4,h5,h6,td,pre,figcaption").any { it != element }) null
                    else SpeechBlock(i, element.wholeText())
                }
                blocks.chunkForSpeech(sentencePauseMs = value.settings.ttsSentencePauseMs, paragraphPauseMs = value.settings.ttsParagraphPauseMs)
            }
            if (parts.isNotEmpty()) { chaptersReset(next, parts); publish(playing = true); speakChunk(); return }
            next++
        }
        pause()
    }
    private fun publish(playing: Boolean = NarrationPlayer.state.value.playing) {
        val part = chunks.getOrNull(chunk)
        val old = NarrationPlayer.state.value
        NarrationPlayer.state.value = old.copy(bookId = request?.book?.id, chapter = chapter,
            block = part?.blockIndex ?: old.block, start = part?.domOffset(offset) ?: old.start,
            end = part?.endOffset ?: old.end, progress = if (chunks.isEmpty()) 0f else chunk.toFloat() / chunks.size,
            playing = playing, paused = !playing, sleepAt = sleepAt, error = null)
        session.setPlaybackState(PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f).build())
        session.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, request?.book?.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, request?.book?.chapters?.getOrNull(chapter)?.title).build())
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager::class.java).notify(211, notification())
    }
    private fun persist() {
        val state = NarrationPlayer.state.value
        if (state.bookId == null) return
        getSharedPreferences("nova_narration", MODE_PRIVATE).edit().putString("book", state.bookId).putInt("chapter", state.chapter)
            .putInt("block", state.block).putInt("offset", state.start).putFloat("progress", state.progress).putLong("time", System.currentTimeMillis()).apply()
    }
    private fun fail(message: String) { pause(); NarrationPlayer.state.value = NarrationPlayer.state.value.copy(error = message) }
    private fun notification(): Notification {
        fun action(name: String) = PendingIntent.getService(this, name.hashCode(), Intent(this, NarrationService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 211, Intent(this, MainActivity::class.java).putExtra("narrationBook", request?.book?.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playing = NarrationPlayer.state.value.playing
        return NotificationCompat.Builder(this, "nova-narration").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(request?.book?.title ?: "Nova Reader").setContentText(request?.book?.chapters?.getOrNull(chapter)?.title ?: "Preparando narración")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(playing)
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pausar" else "Reanudar", action(if (playing) "pause" else "play"))
            .addAction(android.R.drawable.ic_media_next, "Siguiente capítulo", action("next"))
            .addAction(android.R.drawable.ic_delete, "Detener", action("stop"))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
    }
    override fun onDestroy() {
        persist(); scope.cancel(); engine?.stop(); engine?.shutdown(); session.release(); unregisterReceiver(noisy)
        audio.abandonAudioFocusRequest(focus); NarrationPlayer.state.value = NarrationState(); stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
