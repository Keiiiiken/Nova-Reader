package io.luma.reader.audio

import android.media.AudioFormat
import android.speech.tts.*
import java.util.concurrent.atomic.AtomicBoolean

/** Deterministic offline PCM voice for testing lifecycle and media controls, not voice quality. */
class TestSpeechEngine : TextToSpeechService() {
    private val stopped = AtomicBoolean(false)
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?) = TextToSpeech.LANG_COUNTRY_AVAILABLE
    override fun onLoadLanguage(lang: String?, country: String?, variant: String?) = TextToSpeech.LANG_COUNTRY_AVAILABLE
    override fun onGetLanguage() = arrayOf("spa", "ESP", "")
    override fun onStop() { stopped.set(true) }
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped.set(false)
        sendBroadcast(android.content.Intent("io.luma.reader.TEST_TTS_REQUEST")
            .setPackage(packageName.removeSuffix(".test")).putExtra("rate", request.speechRate))
        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1)
        val silence = ByteArray(3200)
        repeat(60) {
            if (stopped.get()) return
            callback.audioAvailable(silence, 0, silence.size)
            Thread.sleep(50)
        }
        callback.done()
    }
}
