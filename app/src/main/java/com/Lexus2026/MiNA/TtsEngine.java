package com.Lexus2026.MiNA;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

class TtsEngine {

    interface Listener {
        void onReady();
        void onDone();
    }

    private final Context ctx;
    private TextToSpeech tts;
    private boolean ready;
    private Listener listener;

    TtsEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    void init() {
        if (tts != null) return;
        tts = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    ready = false;
                    return;
                }
                int res = tts.setLanguage(Locale.getDefault());
                if (res == TextToSpeech.LANG_MISSING_DATA
                        || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(new Locale("es", "ES"));
                }
                tts.setOnUtteranceProgressListener(progress);
                ready = true;
                if (listener != null) listener.onReady();
            }
        });
    }

    void setListener(Listener l) { this.listener = l; }
    boolean isReady() { return ready; }

    void speak(String text) {
        if (!ready || tts == null || text == null || text.isEmpty()) {
            if (listener != null) listener.onDone();
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mina");
    }

    void stop() {
        if (tts != null) tts.stop();
    }

    void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ready = false;
        }
    }

    private final UtteranceProgressListener progress = new UtteranceProgressListener() {
        @Override public void onStart(String id) {}
        @Override public void onDone(String id) {
            if (listener != null) listener.onDone();
        }
        @Override public void onError(String id) {
            if (listener != null) listener.onDone();
        }
    };
}
