package com.Lexus2026.MiNA;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

class FormantTts {

    interface Listener {
        void onReady();
        void onDone();
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Listener listener;
    private AudioTrack track;
    private Thread worker;
    private volatile boolean cancelled;

    FormantTts() {}

    void setListener(Listener l) {
        this.listener = l;
        ui.post(new Runnable() {
				@Override public void run() {
					if (listener != null) listener.onReady();
				}
			});
    }

    boolean isReady() { return true; }

    void speak(final String text) {
        stop();
        cancelled = false;

        worker = new Thread(new Runnable() {
				@Override public void run() {
					try {
						float[] pcmFloat = FormantSynth.synthesize(text);
						if (cancelled || pcmFloat.length == 0) {
							notifyDone();
							return;
						}
						short[] pcm = new short[pcmFloat.length];
						for (int i = 0; i < pcmFloat.length; i++) {
							float v = pcmFloat[i];
							if (v > 1f) v = 1f;
							if (v < -1f) v = -1f;
							pcm[i] = (short)(v * 32000);
						}

						int bufBytes = pcm.length * 2;
						track = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            FormantSynth.SR,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufBytes,
                            AudioTrack.MODE_STATIC);

						track.write(pcm, 0, pcm.length);

						track.play();

						long totalMs = (long)(pcm.length * 1000L / FormantSynth.SR);
						long waited = 0;
						while (!cancelled && waited < totalMs + 500) {
							Thread.sleep(50);
							waited += 50;
							if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING
                                && waited > 200) break;
						}
					} catch (Throwable t) {
						// silencio: si algo falla, al menos no crashea
					} finally {
						releaseTrack();
						notifyDone();
					}
				}
			}, "formant-tts");
        worker.start();
    }

    void stop() {
        cancelled = true;
        releaseTrack();
        if (worker != null && worker.isAlive()) {
            try { worker.join(200); } catch (InterruptedException ignored) {}
        }
        worker = null;
    }

    void destroy() { stop(); }

    private void releaseTrack() {
        if (track != null) {
            try { track.stop(); } catch (Exception ignored) {}
            try { track.release(); } catch (Exception ignored) {}
            track = null;
        }
    }

    private void notifyDone() {
        ui.post(new Runnable() {
				@Override public void run() {
					if (listener != null) listener.onDone();
				}
			});
    }
}
