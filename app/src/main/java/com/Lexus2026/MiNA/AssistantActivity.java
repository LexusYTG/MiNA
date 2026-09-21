package com.Lexus2026.MiNA;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AssistantActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        setContentView(buildUi());

        // Aquí puedes leer extras que el sistema pueda enviar:
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_ASSIST.equals(intent.getAction())) {
            // Por ahora no hay nada más que hacer.
        }
    }

    private View buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER);

        int p = Ui.dp(this, 24);
        root.setPadding(p, p, p, p);

        TextView dot = Ui.text(this, "◉", 48, Ui.ACCENT, true);
        dot.setGravity(Gravity.CENTER);
        root.addView(dot);

        TextView title = Ui.text(this, "MiNA escuchando…", 22, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = Ui.dp(this, 12);
        root.addView(title, tLp);

        TextView hint = Ui.body(this,
								"Aquí irá la lógica del asistente.\nPor ahora es un marcador de posición.");
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = Ui.dp(this, 8);
        root.addView(hint, hLp);

        return root;
    }
}
