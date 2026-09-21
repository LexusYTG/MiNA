package com.Lexus2026.MiNA;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_PERMS          = 200;
    private static final int REQ_ASSISTANT_ROLE = 42;

    private final Lang.Listener langListener = new Lang.Listener() {
        @Override public void onLanguageChanged() {
            runOnUiThread(new Runnable() {
					@Override public void run() { setContentView(buildUi()); }
				});
        }
    };

    private TextView statusDot;
    private TextView statusText;
    private TextView statusHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Lang.init(this);
        Lang.addListener(langListener);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        setContentView(buildUi());
        requestMissingPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        requestMissingPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Lang.removeListener(langListener);
    }

    // =================================================================
    // Permisos runtime
    // =================================================================

    private void requestMissingPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;

        List<String> needed = new ArrayList<String>();

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
			!= PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
			!= PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
			!= PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        // Nada especial. Si deniega, el clima cae a ciudad explícita
        // y el asistente pide el micrófono otra vez cuando se use.
    }

    // =================================================================
    // UI
    // =================================================================

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 32), pad, Ui.dp(this, 32));
        scroll.addView(root);

        root.addView(Ui.h1(this, Lang.get(1)));
        TextView subtitle = Ui.body(this, Lang.get(2));
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.dp(this, 4);
        root.addView(subtitle, subLp);
        root.addView(Ui.space(this, 24));

        LinearLayout statusCard = Ui.card(this);
        LinearLayout statusRow = Ui.row(this);
        statusDot = Ui.text(this, "●", 20, Ui.WARN, true);
        statusRow.addView(statusDot);
        statusText = Ui.text(this, Lang.get(3), 16, Ui.TEXT, true);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.leftMargin = Ui.dp(this, 10);
        statusRow.addView(statusText, stLp);
        statusCard.addView(statusRow);

        statusHint = Ui.body(this, "");
        LinearLayout.LayoutParams hintLp = Ui.matchWrap();
        hintLp.topMargin = Ui.dp(this, 8);
        statusCard.addView(statusHint, hintLp);
        root.addView(statusCard);

        root.addView(Ui.space(this, 16));

        LinearLayout actions = Ui.card(this);
        actions.addView(Ui.h2(this, Lang.get(8)));
        actions.addView(Ui.space(this, 12));

        Button setDefault = Ui.button(this, Lang.get(9), true);
        setDefault.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { requestAssistantRole(); }
			});
        actions.addView(setDefault, Ui.matchWrap());
        actions.addView(Ui.space(this, 10));

        Button openSettings = Ui.button(this, Lang.get(10), false);
        openSettings.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { openAssistantSettings(); }
			});
        actions.addView(openSettings, Ui.matchWrap());
        actions.addView(Ui.space(this, 10));

        Button test = Ui.button(this, Lang.get(11), false);
        test.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					Intent i = new Intent(MainActivity.this, AssistantActivity.class);
					i.setAction(Intent.ACTION_ASSIST);
					startActivity(i);
				}
			});
        actions.addView(test, Ui.matchWrap());

        root.addView(actions);
        root.addView(Ui.space(this, 16));

        LinearLayout langCard = Ui.card(this);
        langCard.addView(Ui.h2(this, Lang.get(12)));
        langCard.addView(Ui.space(this, 8));
        langCard.addView(Ui.body(this,
								 Lang.f(18, Lang.getDisplayName(Lang.getActiveLanguage()))));
        LinearLayout.LayoutParams lbLp = Ui.matchWrap();
        lbLp.topMargin = Ui.dp(this, 10);
        Button changeLang = Ui.button(this, Lang.get(13), false);
        changeLang.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showLanguageDialog(); }
			});
        langCard.addView(changeLang, lbLp);
        root.addView(langCard);

        root.addView(Ui.space(this, 16));

        LinearLayout info = Ui.card(this);
        info.addView(Ui.h2(this, Lang.get(15)));
        info.addView(Ui.space(this, 8));
        info.addView(Ui.body(this, Lang.get(16)));
        root.addView(info);

        root.addView(Ui.space(this, 24));
        TextView foot = Ui.body(this, Lang.get(14));
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, Ui.matchWrap());

        refreshStatus();
        return scroll;
    }

    // =================================================================
    // Diálogo de idiomas
    // =================================================================

    private void showLanguageDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        final LinearLayout panel = Ui.column(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        bg.setCornerRadius(Ui.dp(this, 24));
        panel.setBackground(bg);
        int pad = Ui.dp(this, 20);
        panel.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, Lang.get(17), 20, Ui.TEXT, true);
        panel.addView(title);

        panel.addView(Ui.space(this, 6));

        TextView sub = Ui.body(this,
							   Lang.f(18, Lang.getDisplayName(Lang.getActiveLanguage())));
        panel.addView(sub);

        panel.addView(Ui.space(this, 16));

        final LinearLayout listContainer = Ui.column(this);
        panel.addView(listContainer, Ui.matchWrap());

        panel.addView(Ui.space(this, 12));

        Button cancel = Ui.button(this, Lang.get(20), false);
        cancel.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { dialog.dismiss(); }
			});
        panel.addView(cancel, Ui.matchWrap());

        final Runnable populate = new Runnable() {
            @Override public void run() {
                listContainer.removeAllViews();
                List<String> langs = Lang.getAvailableLanguages();
                String active = Lang.getActiveLanguage();

                if (langs.isEmpty()) {
                    TextView loading = Ui.text(MainActivity.this,
											   Lang.get(21), 14, Ui.TEXT_DIM, false);
                    loading.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams lp = Ui.matchWrap();
                    lp.topMargin = Ui.dp(MainActivity.this, 20);
                    lp.bottomMargin = Ui.dp(MainActivity.this, 20);
                    listContainer.addView(loading, lp);
                    return;
                }

                for (String code : langs) {
                    listContainer.addView(
						buildLangRow(dialog, code, code.equals(active)));
                }
            }
        };

        final Lang.LangListListener listListener = new Lang.LangListListener() {
            @Override public void onLanguagesChanged() {
                runOnUiThread(populate);
            }
        };
        Lang.addLangListListener(listListener);
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
				@Override public void onDismiss(android.content.DialogInterface d) {
					Lang.removeLangListListener(listListener);
				}
			});

        populate.run();

        dialog.setContentView(panel);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.88f);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = width;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            lp.dimAmount = 0.65f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        dialog.show();

        Window w2 = dialog.getWindow();
        if (w2 != null) {
            int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.88f);
            w2.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private View buildLangRow(final Dialog dialog, final String code, boolean isActive) {
        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isActive ? 0xFF1E2430 : Ui.SURFACE_2);
        bg.setCornerRadius(Ui.dp(this, 14));
        if (isActive) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        row.setBackground(bg);

        int p = Ui.dp(this, 12);
        row.setPadding(p, p, p, p);

        TextView badge = Ui.text(this, code.toUpperCase(Locale.ROOT), 14,
								 isActive ? Ui.ACCENT : Ui.TEXT_DIM, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Ui.SURFACE);
        badgeBg.setCornerRadius(Ui.dp(this, 8));
        badge.setBackground(badgeBg);
        badge.setWidth(Ui.dp(this, 42));
        badge.setHeight(Ui.dp(this, 42));
        row.addView(badge);

        TextView name = Ui.text(this, Lang.getDisplayName(code), 15, Ui.TEXT, true);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
			0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nLp.leftMargin = Ui.dp(this, 14);
        row.addView(name, nLp);

        if (isActive) {
            TextView check = Ui.text(this, "✓", 18, Ui.ACCENT, true);
            check.setGravity(Gravity.CENTER);
            row.addView(check);
        }

        row.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					dialog.dismiss();
					if (!code.equals(Lang.getActiveLanguage())) {
						Lang.setLanguage(MainActivity.this, code);
					}
				}
			});

        return row;
    }

    // =================================================================
    // Lógica
    // =================================================================

    private void refreshStatus() {
        if (statusDot == null || statusText == null || statusHint == null) return;
        boolean isDefault = isDefaultAssistant();
        if (isDefault) {
            statusDot.setTextColor(Ui.OK);
            statusText.setText(Lang.get(4));
            statusHint.setText(Lang.get(6));
        } else {
            statusDot.setTextColor(Ui.WARN);
            statusText.setText(Lang.get(5));
            statusHint.setText(Lang.get(7));
        }
    }

    private boolean isDefaultAssistant() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_ASSISTANT);
        }
        @SuppressWarnings("deprecation")
			String assistant = Settings.Secure.getString(
			getContentResolver(), "assistant");
        return assistant != null && assistant.startsWith(getPackageName());
    }

    private void requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (rm != null
				&& rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
				&& !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                Intent i = rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT);
                startActivityForResult(i, REQ_ASSISTANT_ROLE);
                return;
            }
        }
        openAssistantSettings();
    }

    private void openAssistantSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        }
    }
}
