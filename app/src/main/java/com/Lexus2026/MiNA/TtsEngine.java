package com.Lexus2026.MiNA;

class TtsEngine {

    interface Listener {
        void onReady();
        void onDone();
    }

    private final FormantTts engine = new FormantTts();
    private Listener listener;
    private boolean announcedReady;

    TtsEngine() {}

    void init() {
        engine.setListener(new FormantTts.Listener() {
				@Override public void onReady() {}
				@Override public void onDone() { notifyDone(); }
			});
        markReady();
    }

    void setListener(Listener l) {
        this.listener = l;
        markReady();
    }

    private void markReady() {
        if (announcedReady || listener == null) return;
        announcedReady = true;
        listener.onReady();
    }

    boolean isReady() { return true; }

    void speak(String text) {
        if (text == null || text.isEmpty()) { notifyDone(); return; }
        engine.speak(text);
    }

    void stop()    { engine.stop();    }
    void destroy() { engine.destroy(); }

    private void notifyDone() {
        if (listener != null) listener.onDone();
    }
}
