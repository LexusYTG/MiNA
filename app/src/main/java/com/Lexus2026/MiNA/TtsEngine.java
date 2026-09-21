package com.Lexus2026.MiNA;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

class TtsEngine {

    interface Listener {
        void onReady();
        void onDone();
    }

    private TextToSpeech tts;
    private Listener listener;
    private Context ctx;
    private boolean ready;
    private boolean announced;
    private String appliedTag;

    private static final float PITCH = 1.35f;
    private static final float RATE  = 1.00f;

    TtsEngine() {}

    void init(Context context) {
        this.ctx = context.getApplicationContext();

        if (tts != null) {
            applyLocaleAndFinish();
            return;
        }

        tts = new TextToSpeech(this.ctx, new TextToSpeech.OnInitListener() {
				@Override public void onInit(int status) {
					if (status != TextToSpeech.SUCCESS) {
						notifyReady();
						return;
					}
					applyLocaleAndFinish();
				}
			});
    }

    private void applyLocaleAndFinish() {
        if (tts == null) return;

        String tag = Lang.getLocaleTag();
        Locale loc = Locale.forLanguageTag(tag);
        int res = tts.setLanguage(loc);

        if (res == TextToSpeech.LANG_MISSING_DATA
			|| res == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback: intentar solo el idioma base (ej. "es" de "es-ES")
            String base = loc.getLanguage();
            res = tts.setLanguage(new Locale(base));
        }
        if (res == TextToSpeech.LANG_MISSING_DATA
			|| res == TextToSpeech.LANG_NOT_SUPPORTED) {
            res = tts.setLanguage(Locale.getDefault());
        }

        if (res == TextToSpeech.LANG_MISSING_DATA
			|| res == TextToSpeech.LANG_NOT_SUPPORTED) {
            ready = false;
            notifyReady();
            return;
        }

        tts.setPitch(PITCH);
        tts.setSpeechRate(RATE);
        tts.setOnUtteranceProgressListener(progress);
        appliedTag = tag;
        ready = true;
        notifyReady();
    }

    void openVoiceSettings() {
        if (ctx == null) return;

        Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStart(i)) return;

        Intent s = new Intent("com.android.settings.TTS_SETTINGS");
        s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStart(s)) return;

        Intent g = new Intent(android.provider.Settings.ACTION_SETTINGS);
        g.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tryStart(g);
    }

    private boolean tryStart(Intent i) {
        try {
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void setListener(Listener l) {
        this.listener = l;
        if (ready) notifyReady();
    }

    private void notifyReady() {
        if (announced || listener == null) return;
        announced = true;
        listener.onReady();
    }

    boolean isReady() { return ready; }

    void speak(String text) {
        if (text == null || text.isEmpty()) { notifyDone(); return; }
        if (!ready || tts == null) { notifyDone(); return; }

        // Si el idioma activo cambió desde el último init, reaplicarlo.
        String current = Lang.getLocaleTag();
        if (appliedTag != null && !appliedTag.equals(current)) {
            applyLocaleAndFinish();
        }

        Bundle params = new Bundle();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "mina");
    }

    void stop() {
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
        }
    }

    void destroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        ready = false;
        appliedTag = null;
    }

    private void notifyDone() {
        if (listener != null) listener.onDone();
    }

    private final UtteranceProgressListener progress = new UtteranceProgressListener() {
        @Override public void onStart(String utteranceId) {}
        @Override public void onDone(String utteranceId) { notifyDone(); }
        @Override public void onError(String utteranceId) { notifyDone(); }
    };
}
