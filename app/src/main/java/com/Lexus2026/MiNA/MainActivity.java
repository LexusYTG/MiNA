package com.Lexus2026.MiNA;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
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

    private TextView statusDot;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    // ------------------------------------------------------------ UI

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 32), pad, Ui.dp(this, 32));
        scroll.addView(root);

        // Cabecera
        root.addView(Ui.h1(this, "MiNA"));

        TextView subtitle = Ui.body(this, "Asistente personal para Android");
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.dp(this, 4);
        root.addView(subtitle, subLp);

        root.addView(Ui.space(this, 24));

        // Tarjeta de estado
        LinearLayout statusCard = Ui.card(this);
        LinearLayout statusRow = Ui.row(this);
        statusDot = Ui.text(this, "●", 20, Ui.WARN, true);
        statusRow.addView(statusDot);
        statusText = Ui.text(this, "Comprobando…", 16, Ui.TEXT, true);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.leftMargin = Ui.dp(this, 10);
        statusRow.addView(statusText, stLp);
        statusCard.addView(statusRow);

        TextView statusHint = Ui.body(this,
									  "Estado del rol de asistente predeterminado del sistema.");
        LinearLayout.LayoutParams hintLp = Ui.matchWrap();
        hintLp.topMargin = Ui.dp(this, 8);
        statusCard.addView(statusHint, hintLp);
        root.addView(statusCard);

        root.addView(Ui.space(this, 16));

        // Tarjeta de acciones
        LinearLayout actions = Ui.card(this);
        actions.addView(Ui.h2(this, "Acciones"));
        actions.addView(Ui.space(this, 12));

        Button setDefault = Ui.button(this, "Establecer como asistente", true);
        setDefault.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { requestAssistantRole(); }
			});
        actions.addView(setDefault, Ui.matchWrap());

        actions.addView(Ui.space(this, 10));

        Button openSettings = Ui.button(this, "Abrir ajustes de asistente", false);
        openSettings.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { openAssistantSettings(); }
			});
        actions.addView(openSettings, Ui.matchWrap());

        actions.addView(Ui.space(this, 10));

        Button test = Ui.button(this, "Probar como asistente", false);
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

        // Tarjeta informativa
        LinearLayout info = Ui.card(this);
        info.addView(Ui.h2(this, "Cómo activarlo"));
        info.addView(Ui.space(this, 8));
        info.addView(Ui.body(this,
							 "1. Pulsa «Establecer como asistente».\n" +
							 "2. Confirma en el diálogo del sistema.\n" +
							 "3. Mantén pulsado el botón de inicio (o el gesto de asistente) " +
							 "para invocar a MiNA."));
        root.addView(info);

        root.addView(Ui.space(this, 24));
        TextView foot = Ui.body(this, "MiNA · v0.1");
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, Ui.matchWrap());

        return scroll;
    }

    // ------------------------------------------------------- Lógica

    private void refreshStatus() {
        boolean isDefault = isDefaultAssistant();
        if (isDefault) {
            statusDot.setTextColor(Ui.OK);
            statusText.setText("Activo como asistente");
        } else {
            statusDot.setTextColor(Ui.WARN);
            statusText.setText("No es el asistente predeterminado");
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isDefaultAssistant() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_ASSISTANT);
        }
        // Fallback para versiones previas a Android 10
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
        // En Android < 10 no hay diálogo de rol, se abre ajustes manualmente
        openAssistantSettings();
    }

    private void openAssistantSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) { /* nada */ }
        }
    }
}
