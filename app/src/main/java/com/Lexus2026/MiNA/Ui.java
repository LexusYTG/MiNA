package com.Lexus2026.MiNA;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {

    static final int BG        = 0xFF0B0F14;
    static final int SURFACE   = 0xFF161B22;
    static final int SURFACE_2 = 0xFF232B36;
    static final int ACCENT    = 0xFF7C4DFF;
    static final int TEXT      = 0xFFECEFF4;
    static final int TEXT_DIM  = 0xFF9AA4B2;
    static final int OK        = 0xFF4CAF50;
    static final int WARN      = 0xFFFFA726;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
							  TypedValue.COMPLEX_UNIT_DIP, v,
							  c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static TextView text(Context c, String s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setLineSpacing(0f, 1.15f);
        return t;
    }

    static TextView h1(Context c, String s)   { return text(c, s, 26, TEXT, true); }
    static TextView h2(Context c, String s)   { return text(c, s, 18, TEXT, true); }
    static TextView body(Context c, String s) { return text(c, s, 14, TEXT_DIM, false); }

    static LinearLayout card(Context c) {
        LinearLayout card = column(c);
        card.setBackground(round(SURFACE, 16, c));
        int p = dp(c, 16);
        card.setPadding(p, p, p, p);
        return card;
    }

    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setBackground(round(primary ? ACCENT : SURFACE_2, 12, c));
        b.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    static View space(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
							  LinearLayout.LayoutParams.MATCH_PARENT, dp(c, h)));
        return v;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
