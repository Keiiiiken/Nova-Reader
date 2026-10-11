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
    @After fun stop() { context.stopService(android.content.Intent(context, NarrationService::class.java)) }
    @Test fun narrationSurvivesClosingActivityAndSupportsPauseResumeAndTimer() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val book = Book("audio-fixture", "Libro narrado", "Autora", "", "", chapters = listOf(Chapter("Uno", "")))
        val settings = ReaderSettings(ttsEngine = InstrumentationRegistry.getInstrumentation().context.packageName, ttsLanguageTag = "es-ES", ttsAutoAdvance = false)
        val chunks = listOf(SpeechBlock(0, "Primera frase para probar la narración. Segunda frase para comprobar la pausa.")).chunkForSpeech()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { NarrationPlayer.start(context, NarrationRequest(book, settings, 0, chunks, 0, 0)) }
        await { NarrationPlayer.state.value.playing }
        scenario.close()
        assertTrue(NarrationPlayer.state.value.playing)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 211 })
        NarrationPlayer.command(context, "pause")
        await { NarrationPlayer.state.value.paused }
        NarrationPlayer.command(context, "timer", 10)
        await { NarrationPlayer.state.value.sleepAt > System.currentTimeMillis() }
        NarrationPlayer.command(context, "timer", 0)
        await { NarrationPlayer.state.value.sleepAt == 0L }
        NarrationPlayer.command(context, "play")
        await { NarrationPlayer.state.value.playing }
        context.sendBroadcast(android.content.Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY).setPackage(context.packageName))
        await { NarrationPlayer.state.value.paused }
    }
    private fun await(condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 15000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        assertTrue("Narration state: ${NarrationPlayer.state.value}", condition())
    }
}
