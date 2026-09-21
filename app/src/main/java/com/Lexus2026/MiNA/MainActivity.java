package com.Lexus2026.MiNA;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Lang.removeListener(langListener);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 32), pad, Ui.dp(this, 32));
        scroll.addView(root);

        root.addView(Ui.h1(this, Lang.get(1000)));
        TextView subtitle = Ui.body(this, Lang.get(1001));
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.dp(this, 4);
        root.addView(subtitle, subLp);
        root.addView(Ui.space(this, 24));

        LinearLayout statusCard = Ui.card(this);
        LinearLayout statusRow = Ui.row(this);
        statusDot = Ui.text(this, "●", 20, Ui.WARN, true);
        statusRow.addView(statusDot);
        statusText = Ui.text(this, Lang.get(1002), 16, Ui.TEXT, true);
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
        actions.addView(Ui.h2(this, Lang.get(1007)));
        actions.addView(Ui.space(this, 12));

        Button setDefault = Ui.button(this, Lang.get(1008), true);
        setDefault.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { requestAssistantRole(); }
			});
        actions.addView(setDefault, Ui.matchWrap());
        actions.addView(Ui.space(this, 10));

        Button openSettings = Ui.button(this, Lang.get(1009), false);
        openSettings.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { openAssistantSettings(); }
			});
        actions.addView(openSettings, Ui.matchWrap());
        actions.addView(Ui.space(this, 10));

        Button test = Ui.button(this, Lang.get(1010), false);
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
        langCard.addView(Ui.h2(this, Lang.get(1011)));
        langCard.addView(Ui.space(this, 8));
        langCard.addView(Ui.body(this,
								 Lang.f(1051, Lang.getDisplayName(Lang.getActiveLanguage()))));
        LinearLayout.LayoutParams lbLp = Ui.matchWrap();
        lbLp.topMargin = Ui.dp(this, 10);
        Button changeLang = Ui.button(this, Lang.get(1012), false);
        changeLang.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showLanguageDialog(); }
			});
        langCard.addView(changeLang, lbLp);
        root.addView(langCard);

        root.addView(Ui.space(this, 16));

        LinearLayout info = Ui.card(this);
        info.addView(Ui.h2(this, Lang.get(1014)));
        info.addView(Ui.space(this, 8));
        info.addView(Ui.body(this, Lang.get(1015)));
        root.addView(info);

        root.addView(Ui.space(this, 24));
        TextView foot = Ui.body(this, Lang.get(1013));
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, Ui.matchWrap());

        refreshStatus();
        return scroll;
    }

    private void showLanguageDialog() {
        final java.util.List<String> available = Lang.getAvailableLanguages();
        final String[] codes = available.toArray(new String[0]);
        String[] names = new String[codes.length];
        for (int i = 0; i < codes.length; i++) names[i] = Lang.getDisplayName(codes[i]);

        new AlertDialog.Builder(this)
			.setTitle(Lang.get(1050))
			.setItems(names, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface d, int which) {
					Lang.setLanguage(MainActivity.this, codes[which]);
				}
			})
			.setNegativeButton(Lang.get(1052), null)
			.show();
    }

    private void refreshStatus() {
        if (statusDot == null || statusText == null || statusHint == null) return;
        boolean isDefault = isDefaultAssistant();
        if (isDefault) {
            statusDot.setTextColor(Ui.OK);
            statusText.setText(Lang.get(1003));
            statusHint.setText(Lang.get(1005));
        } else {
            statusDot.setTextColor(Ui.WARN);
            statusText.setText(Lang.get(1004));
            statusHint.setText(Lang.get(1006));
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
                startActivityForResult(i, 42);
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
