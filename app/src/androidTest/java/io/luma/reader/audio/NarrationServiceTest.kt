package io.luma.reader.audio

import android.content.Context
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.luma.reader.MainActivity
import io.luma.reader.model.*
import io.luma.reader.ui.SpeechBlock
import io.luma.reader.ui.chunkForSpeech
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NarrationServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val spokenRate = java.util.concurrent.atomic.AtomicInteger()
    private val observer = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) { spokenRate.set(intent?.getIntExtra("rate", 0) ?: 0) }
    }
    @Before fun observeEngine() {
        androidx.core.content.ContextCompat.registerReceiver(context, observer, android.content.IntentFilter("io.luma.reader.TEST_TTS_REQUEST"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }
    @After fun stop() {
        context.stopService(android.content.Intent(context, NarrationService::class.java))
        context.unregisterReceiver(observer)
    }
    @Test fun narrationSurvivesClosingActivityAndSupportsPauseResumeAndTimer() {
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val book = Book("audio-fixture", "Libro narrado", "Autora", "", "", chapters = listOf(Chapter("Uno", "")))
        val settings = ReaderSettings(ttsEngine = InstrumentationRegistry.getInstrumentation().context.packageName, ttsLanguageTag = "es-ES", ttsAutoAdvance = false)
        val chunks = listOf(SpeechBlock(0, "Primera frase para probar la narración. Segunda frase para comprobar la pausa.")).chunkForSpeech()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { NarrationPlayer.start(context, NarrationRequest(book, settings, 0, chunks, 0, 0)) }
        await { NarrationPlayer.state.value.playing && spokenRate.get() == 100 }
        NarrationPlayer.updateSettings(context, book.id, settings.copy(ttsRate = 1.5f))
        await { spokenRate.get() == 150 }
        assertTrue(NarrationPlayer.state.value.playing)
        scenario.close()
        assertTrue(NarrationPlayer.state.value.playing)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 211 })
        NarrationPlayer.command(context, "pause")
        await { NarrationPlayer.state.value.paused }
        NarrationPlayer.command(context, "timer", 10)
        await { NarrationPlayer.state.value.sleepAt > System.currentTimeMillis() }
        NarrationPlayer.command(context, "timer", 0)
        await { NarrationPlayer.state.value.sleepAt == 0L }
        NarrationPlayer.updateSettings(context, book.id, settings.copy(ttsRate = 2f))
        assertTrue(NarrationPlayer.state.value.paused)
        NarrationPlayer.command(context, "play")
        await { NarrationPlayer.state.value.playing && spokenRate.get() == 200 }
        context.sendBroadcast(android.content.Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY).setPackage(context.packageName))
        await { NarrationPlayer.state.value.paused }
    }
    private fun await(condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 15000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        assertTrue("Narration state: ${NarrationPlayer.state.value}", condition())
    }
}
