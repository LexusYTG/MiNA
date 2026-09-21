package com.Lexus2026.MiNA;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AssistantActivity extends Activity {

    private static final int REQ_MIC = 100;

    private final Lang.Listener langListener = new Lang.Listener() {
        @Override public void onLanguageChanged() {
            runOnUiThread(new Runnable() {
					@Override public void run() {
						stopPulse();
						setContentView(buildUi());
					}
				});
        }
    };

    private VoiceEngine voice;
    private TtsEngine   tts;
    private CommandRouter router;

    private TextView micOrb;
    private TextView statusText;
    private TextView partialText;
    private LinearLayout transcript;
    private Button voiceSetupButton;
    private LinearLayout panel;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private boolean launchedAsAssist;
    private boolean pendingAutoListen;
    private boolean pulseOn;
    private int     pulseStep;
    private boolean greeted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Lang.init(this);
        Lang.addListener(langListener);

        Intent incoming = getIntent();
        String action = (incoming != null && incoming.getAction() != null)
			? incoming.getAction() : Lang.get(291);

        launchedAsAssist = Intent.ACTION_ASSIST.equals(action)
			|| Intent.ACTION_VOICE_COMMAND.equals(action);

        setContentView(buildUi());

        router = new CommandRouter(this);

        voice = new VoiceEngine(this);
        voice.setListener(voiceListener);
        voice.init();

        tts = new TtsEngine();
        tts.setListener(ttsListener);
        tts.init(this);

        setStatus(Lang.f(31, action), Ui.TEXT_DIM);

        updateVoiceSetupButton();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getAction() != null) {
            setStatus(Lang.f(31, intent.getAction()), Ui.TEXT_DIM);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tts != null && !tts.isReady()) {
            tts.init(this);
        }
        updateVoiceSetupButton();

        if (!greeted && transcript != null && transcript.getChildCount() == 0) {
            greeted = true;
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
        Lang.removeListener(langListener);
        voice.destroy();
        tts.destroy();
        ui.removeCallbacksAndMessages(null);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);

        View dim = new View(this);
        dim.setBackgroundColor(0x99000000);
        dim.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { finish(); }
			});
        root.addView(dim, new FrameLayout.LayoutParams(
						 FrameLayout.LayoutParams.MATCH_PARENT,
						 FrameLayout.LayoutParams.MATCH_PARENT));

        panel = Ui.column(this);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        float r = Ui.dp(this, 28);
        bg.setCornerRadii(new float[]{ r, r, r, r, 0, 0, 0, 0 });
        panel.setBackground(bg);

        View handle = new View(this);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0xFF3A4250);
        handleBg.setCornerRadius(Ui.dp(this, 2));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
			Ui.dp(this, 36), Ui.dp(this, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = Ui.dp(this, 8);
        panel.addView(handle, handleLp);

        panel.addView(Ui.space(this, 12));

        micOrb = Ui.text(this, "◉", 44, Ui.ACCENT, true);
        micOrb.setGravity(Gravity.CENTER);
        panel.addView(micOrb, Ui.matchWrap());
        micOrb.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { toggleListening(); }
			});

        statusText = Ui.text(this, "", 14, Ui.TEXT_DIM, false);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stLp = Ui.matchWrap();
        stLp.topMargin = Ui.dp(this, 6);
        panel.addView(statusText, stLp);

        voiceSetupButton = Ui.button(this, Lang.get(29), false);
        voiceSetupButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams vLp = Ui.matchWrap();
        vLp.topMargin = Ui.dp(this, 10);
        vLp.leftMargin = Ui.dp(this, 24);
        vLp.rightMargin = Ui.dp(this, 24);
        panel.addView(voiceSetupButton, vLp);
        voiceSetupButton.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { tts.openVoiceSettings(); }
			});

        partialText = Ui.text(this, "", 16, Ui.TEXT, false);
        partialText.setGravity(Gravity.CENTER);
        partialText.setMinLines(1);
        LinearLayout.LayoutParams ptLp = Ui.matchWrap();
        ptLp.topMargin = Ui.dp(this, 14);
        ptLp.leftMargin = Ui.dp(this, 16);
        ptLp.rightMargin = Ui.dp(this, 16);
        panel.addView(partialText, ptLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.round(Ui.SURFACE_2, 16, this));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scLp.topMargin = Ui.dp(this, 12);
        scLp.leftMargin = Ui.dp(this, 16);
        scLp.rightMargin = Ui.dp(this, 16);
        scLp.bottomMargin = Ui.dp(this, 20);
        scroll.setLayoutParams(scLp);

        transcript = Ui.column(this);
        int sp = Ui.dp(this, 12);
        scroll.setPadding(sp, sp, sp, sp);
        scroll.addView(transcript, Ui.matchWrap());
        panel.addView(scroll);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
			FrameLayout.LayoutParams.MATCH_PARENT,
			FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.BOTTOM;
        root.addView(panel, panelParams);

        panel.post(new Runnable() {
				@Override public void run() {
					DisplayMetrics dm = getResources().getDisplayMetrics();
					int maxH = (int)(dm.heightPixels * 0.75f);
					int measured = panel.getHeight();
					if (measured > maxH) {
						ViewGroup.LayoutParams lp = panel.getLayoutParams();
						lp.height = maxH;
						panel.setLayoutParams(lp);
					}
				}
			});

        return root;
    }

    private void updateVoiceSetupButton() {
        if (voiceSetupButton == null) return;
        boolean show = tts != null && !tts.isReady();
        voiceSetupButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void greet() {
        say(Lang.get(49), launchedAsAssist);
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
                setStatus(Lang.get(28), Ui.WARN);
            }
        }
    }

    private void handleUserText(String text) {
        appendLine(Lang.get(32), text, Ui.TEXT);
        router.routeAsync(text, new CommandRouter.Callback() {
				@Override public void onResult(final CommandRouter.Response r) {
					runOnUiThread(new Runnable() {
							@Override public void run() {
								say(r.text, false);
								if (r.intent != null) {
									try {
										startActivity(r.intent);
									} catch (Exception e) {
										setStatus(Lang.get(30), Ui.WARN);
										return;
									}
								}
								if (r.closeAfter) {
									ui.postDelayed(new Runnable() {
											@Override public void run() { finish(); }
										}, 1400);
								}
							}
						});
				}
			});
    }

    private final VoiceEngine.Listener voiceListener = new VoiceEngine.Listener() {
        @Override public void onReady() {
            startPulse();
            setStatus(Lang.get(26), Ui.ACCENT);
            partialText.setText("");
        }
        @Override public void onPartial(String text) { partialText.setText(text); }
        @Override public void onFinal(String text) {
            stopPulse();
            partialText.setText("");
            if (text == null || text.trim().isEmpty()) {
                setStatus(Lang.get(27), Ui.WARN);
                return;
            }
            handleUserText(text);
        }
        @Override public void onError(String friendly) {
            stopPulse();
            partialText.setText("");
            setStatus(friendly, Ui.WARN);
        }
        @Override public void onEnd() {}
    };

    private final TtsEngine.Listener ttsListener = new TtsEngine.Listener() {
        @Override public void onReady() {
            runOnUiThread(new Runnable() {
					@Override public void run() { updateVoiceSetupButton(); }
				});
        }
        @Override public void onDone() {
            if (pendingAutoListen && !isFinishing()) {
                pendingAutoListen = false;
                if (checkMic()) voice.start();
                return;
            }
            if (!isFinishing()) {
                setStatus(Lang.get(24), Ui.TEXT_DIM);
            }
        }
    };

    private void say(String text, boolean chainListen) {
        appendLine(Lang.get(33), text, Ui.ACCENT);
        pendingAutoListen = chainListen;
        tts.speak(text);
    }

    private void setStatus(String s, int color) {
        if (statusText == null) return;
        statusText.setText(s);
        statusText.setTextColor(color);
    }

    private void appendLine(String who, String text, int color) {
        if (transcript == null) return;
        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(this, 6);
        row.setLayoutParams(lp);

        row.addView(Ui.text(this, who + ": ", 13, color, true));
        row.addView(Ui.text(this, text, 13, Ui.TEXT, false));
        transcript.addView(row);

        final ScrollView sv = (ScrollView) transcript.getParent();
        sv.post(new Runnable() {
				@Override public void run() { sv.fullScroll(View.FOCUS_DOWN); }
			});
    }

    private void startPulse() {
        if (pulseOn) return;
        pulseOn = true;
        pulseStep = 0;
        ui.post(pulseRunnable);
    }

    private void stopPulse() {
        pulseOn = false;
        ui.removeCallbacks(pulseRunnable);
        if (micOrb != null) {
            micOrb.setTextSize(44);
            micOrb.setTextColor(Ui.ACCENT);
        }
    }

    private final Runnable pulseRunnable = new Runnable() {
        @Override public void run() {
            if (!pulseOn || micOrb == null) return;
            pulseStep++;
            micOrb.setTextSize(pulseStep % 2 == 0 ? 44 : 50);
            micOrb.setTextColor(pulseStep % 2 == 0 ? Ui.ACCENT : 0xFFB39DFF);
            ui.postDelayed(this, 450);
        }
    };
}
