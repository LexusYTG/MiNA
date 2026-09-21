package com.Lexus2026.MiNA;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AssistantActivity extends Activity {

    private static final int REQ_MIC = 100;

    private VoiceEngine voice;
    private TtsEngine   tts;
    private CommandRouter router;

    private TextView micOrb;
    private TextView statusText;
    private TextView partialText;
    private LinearLayout transcript;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private boolean launchedAsAssist;
    private boolean pendingAutoListen;
    private boolean pulseOn;
    private int     pulseStep;

    // ------------------------------------------------------------- ciclo

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildUi());

        launchedAsAssist = Intent.ACTION_ASSIST.equals(getIntent().getAction());

        router = new CommandRouter();

        voice = new VoiceEngine(this);
        voice.setListener(voiceListener);

        tts = new TtsEngine(this);
        tts.setListener(ttsListener);
        tts.init();

        setStatus(voice.isAvailable()
				  ? "Pulsa el orbe para hablar"
				  : "Reconocimiento de voz no disponible",
				  voice.isAvailable() ? Ui.TEXT_DIM : Ui.WARN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (transcript.getChildCount() == 0) {
            ui.postDelayed(new Runnable() {
					@Override public void run() { if (!isFinishing()) greet(); }
				}, 250);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        voice.cancel();
        tts.stop();
        stopPulse();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        voice.destroy();
        tts.destroy();
        ui.removeCallbacksAndMessages(null);
    }

    // --------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 20);
        root.setPadding(p, Ui.dp(this, 24), p, Ui.dp(this, 20));

        micOrb = Ui.text(this, "◉", 64, Ui.ACCENT, true);
        micOrb.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams orbLp = Ui.matchWrap();
        orbLp.topMargin = Ui.dp(this, 8);
        root.addView(micOrb, orbLp);
        micOrb.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { toggleListening(); }
			});

        statusText = Ui.text(this, "", 15, Ui.TEXT_DIM, false);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stLp = Ui.matchWrap();
        stLp.topMargin = Ui.dp(this, 4);
        root.addView(statusText, stLp);

        partialText = Ui.text(this, "", 18, Ui.TEXT, false);
        partialText.setGravity(Gravity.CENTER);
        partialText.setMinLines(2);
        LinearLayout.LayoutParams ptLp = Ui.matchWrap();
        ptLp.topMargin = Ui.dp(this, 20);
        root.addView(partialText, ptLp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scLp.topMargin = Ui.dp(this, 16);
        scroll.setLayoutParams(scLp);
        scroll.setBackground(Ui.round(Ui.SURFACE, 16, this));
        int sp = Ui.dp(this, 14);
        scroll.setPadding(sp, sp, sp, sp);

        transcript = Ui.column(this);
        scroll.addView(transcript, Ui.matchWrap());
        root.addView(scroll);

        return root;
    }

    // ------------------------------------------------------------ flujo

    private void greet() {
        say("Hola, soy MiNA. ¿En qué te ayudo?", launchedAsAssist);
    }

    private void toggleListening() {
        if (voice.isListening()) { voice.stop(); return; }
        tts.stop();
        if (checkMic()) voice.start();
    }

    private boolean checkMic() {
        if (Build.VERSION.SDK_INT >= 23
			&& checkSelfPermission(Manifest.permission.RECORD_AUDIO)
			!= PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        if (rc == REQ_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                voice.start();
            } else {
                setStatus("Permiso de micrófono denegado", Ui.WARN);
            }
        }
    }

    private void handleUserText(String text) {
        appendLine("Tú", text, Ui.TEXT);
        CommandRouter.Response r = router.route(text);
        appendLine("MiNA", r.text, Ui.ACCENT);
        say(r.text, false);

        if ("finish".equals(r.action)) {
            ui.postDelayed(new Runnable() {
					@Override public void run() { finish(); }
				}, 1400);
        }
    }

    // ------------------------------------------------------- listeners

    private final VoiceEngine.Listener voiceListener = new VoiceEngine.Listener() {
        @Override public void onReady() {
            startPulse();
            setStatus("Escuchando…", Ui.ACCENT);
            partialText.setText("");
        }
        @Override public void onPartial(String text) {
            partialText.setText(text);
        }
        @Override public void onFinal(String text) {
            stopPulse();
            partialText.setText("");
            if (text == null || text.trim().isEmpty()) {
                setStatus("No te entendí, inténtalo de nuevo", Ui.WARN);
                return;
            }
            handleUserText(text);
        }
        @Override public void onError(String friendly) {
            stopPulse();
            partialText.setText("");
            setStatus(friendly, Ui.WARN);
        }
        @Override public void onEnd() { /* no-op */ }
    };

    private final TtsEngine.Listener ttsListener = new TtsEngine.Listener() {
        @Override public void onReady() { /* nada */ }
        @Override public void onDone() {
            if (pendingAutoListen && !isFinishing()) {
                pendingAutoListen = false;
                if (checkMic()) voice.start();
                return;
            }
            if (!isFinishing()) {
                setStatus("Pulsa el orbe para hablar", Ui.TEXT_DIM);
            }
        }
    };

    // -------------------------------------------------------- utilidades

    private void say(String text, boolean chainListen) {
        appendLine("MiNA", text, Ui.ACCENT);
        if (tts.isReady()) {
            pendingAutoListen = chainListen;
            tts.speak(text);
        } else {
            // TTS aún no listo: pequeño respiro y a escuchar
            if (chainListen) {
                ui.postDelayed(new Runnable() {
						@Override public void run() {
							if (checkMic()) voice.start();
						}
					}, 900);
            }
        }
    }

    private void setStatus(String s, int color) {
        statusText.setText(s);
        statusText.setTextColor(color);
    }

    private void appendLine(String who, String text, int color) {
        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(this, 8);
        row.setLayoutParams(lp);

        row.addView(Ui.text(this, who + ": ", 14, color, true));
        row.addView(Ui.text(this, text, 14, Ui.TEXT, false));
        transcript.addView(row);

        final ScrollView sv = (ScrollView) transcript.getParent();
        sv.post(new Runnable() {
				@Override public void run() { sv.fullScroll(View.FOCUS_DOWN); }
			});
    }

    // ------------------------------------------------------ animación

    private void startPulse() {
        if (pulseOn) return;
        pulseOn = true;
        pulseStep = 0;
        ui.post(pulseRunnable);
    }

    private void stopPulse() {
        pulseOn = false;
        ui.removeCallbacks(pulseRunnable);
        micOrb.setTextSize(64);
        micOrb.setTextColor(Ui.ACCENT);
    }

    private final Runnable pulseRunnable = new Runnable() {
        @Override public void run() {
            if (!pulseOn) return;
            pulseStep++;
            micOrb.setTextSize(pulseStep % 2 == 0 ? 64 : 72);
            micOrb.setTextColor(pulseStep % 2 == 0 ? Ui.ACCENT : 0xFFB39DFF);
            ui.postDelayed(this, 450);
        }
    };
}
