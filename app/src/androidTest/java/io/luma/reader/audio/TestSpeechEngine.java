package io.luma.reader.audio;

import android.content.Intent;
import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone test-APK service: platform APIs only, since AGP shares Kotlin with the target APK. */
public final class TestSpeechEngine extends TextToSpeechService {
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    @Override protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_COUNTRY_AVAILABLE;
    }
    @Override protected int onLoadLanguage(String lang, String country, String variant) {
        return TextToSpeech.LANG_COUNTRY_AVAILABLE;
    }
    @Override protected String[] onGetLanguage() { return new String[] {"spa", "ESP", ""}; }
    @Override protected void onStop() { stopped.set(true); }
    @Override protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        stopped.set(false);
        String target = getPackageName();
        if (target.endsWith(".test")) target = target.substring(0, target.length() - 5);
        sendBroadcast(new Intent("io.luma.reader.TEST_TTS_REQUEST")
                .setPackage(target).putExtra("rate", request.getSpeechRate()));
        callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        byte[] silence = new byte[3200];
        for (int i = 0; i < 60; i++) {
            if (stopped.get()) return;
            callback.audioAvailable(silence, 0, silence.length);
            try { Thread.sleep(50); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
        }
        callback.done();
    }
}
