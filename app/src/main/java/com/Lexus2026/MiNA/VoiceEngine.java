package com.Lexus2026.MiNA;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

class VoiceEngine {

    interface Listener {
        void onReady();
        void onPartial(String text);
        void onFinal(String text);
        void onError(String friendly);
        void onEnd();
    }

    private final Context ctx;
    private SpeechRecognizer recognizer;
    private Listener listener;
    private volatile boolean listening;

    VoiceEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(ctx);
    }

    boolean isListening() { return listening; }

    void setListener(Listener l) { this.listener = l; }

    void init() {
        if (listener != null) listener.onReady();
    }

    void start() {
        if (listening) return;
        if (!isAvailable()) {
            if (listener != null) listener.onError(Lang.get(1159));
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
            recognizer.setRecognitionListener(internal);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
				   RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Lang.getLocaleTag());
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Lang.getLocaleTag());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        listening = true;
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            listening = false;
            if (listener != null) listener.onError(Lang.get(1160));
        }
    }

    void stop() {
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
        }
        listening = false;
    }

    void cancel() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
        }
        listening = false;
    }

    void destroy() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
        listening = false;
    }

    private final RecognitionListener internal = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {
            if (listener != null) listener.onReady();
        }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {
            if (listener != null) listener.onEnd();
        }
        @Override public void onError(int error) {
            listening = false;
            if (listener != null) listener.onError(mapError(error));
        }
        @Override public void onResults(Bundle results) {
            listening = false;
            ArrayList<String> list = results.getStringArrayList(
				SpeechRecognizer.RESULTS_RECOGNITION);
            String text = (list != null && !list.isEmpty()) ? list.get(0) : "";
            if (listener != null) listener.onFinal(text);
        }
        @Override public void onPartialResults(Bundle partial) {
            ArrayList<String> list = partial.getStringArrayList(
				SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty() && listener != null) {
                listener.onPartial(list.get(0));
            }
        }
        @Override public void onEvent(int eventType, Bundle params) {}
    };

    private String mapError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                return Lang.get(1151);
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return Lang.get(1152);
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return Lang.get(1153);
            case SpeechRecognizer.ERROR_AUDIO:
                return Lang.get(1154);
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return Lang.get(1150);
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return Lang.get(1155);
            case SpeechRecognizer.ERROR_SERVER:
                return Lang.get(1156);
            case SpeechRecognizer.ERROR_CLIENT:
                return Lang.get(1157);
            default:
                return Lang.f(1163, error);
        }
    }
}
