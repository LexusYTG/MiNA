package com.Lexus2026.MiNA;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

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
    private boolean listening;

    VoiceEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(ctx);
    }

    boolean isListening() { return listening; }

    void setListener(Listener l) { this.listener = l; }

    void start() {
        if (listening || !isAvailable()) {
            if (listener != null && !isAvailable())
                listener.onError("Reconocimiento de voz no disponible");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
            recognizer.setRecognitionListener(internal);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
				   RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
				   Locale.getDefault().toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        listening = true;
        recognizer.startListening(i);
    }

    void stop() {
        if (recognizer != null) recognizer.stopListening();
        listening = false;
    }

    void cancel() {
        if (recognizer != null) recognizer.cancel();
        listening = false;
    }

    void destroy() {
        if (recognizer != null) {
            recognizer.destroy();
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
            case SpeechRecognizer.ERROR_NO_MATCH:        return "No te entendí";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:  return "No escuché nada";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Sin conexión";
            case SpeechRecognizer.ERROR_AUDIO:           return "Error de audio";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Falta permiso de micrófono";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Ocupado, reintenta";
            case SpeechRecognizer.ERROR_SERVER:          return "Error del servidor";
            case SpeechRecognizer.ERROR_CLIENT:
            default:                                     return "Error (" + error + ")";
        }
    }
}
