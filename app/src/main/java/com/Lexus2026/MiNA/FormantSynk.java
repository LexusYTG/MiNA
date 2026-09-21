package com.Lexus2026.MiNA;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Sintetizador de voz por formantes. Genera PCM mono float en [-1, 1].
 * Sin archivos, sin dependencias. Español.
 */
class FormantSynth {

    static final int SR = 22050;
    static final float F0 = 120f;    // tono base (voz masculina)

    // --------------------------------------------------------- fonemas

    static class Phone {
        float f1, f2, f3;
        float bw1, bw2, bw3;
        float voiced;   // amplitud de la fuente glotal [0..1]
        float noise;    // amplitud del ruido [0..1]
        float amp;      // ganancia de salida
        float dur;      // segundos
        float a1, a2, a3; // pesos de formantes en la mezcla
        Phone(float f1,float f2,float f3,float bw1,float bw2,float bw3,
              float voiced,float noise,float amp,float dur,
              float a1,float a2,float a3) {
            this.f1=f1; this.f2=f2; this.f3=f3;
            this.bw1=bw1; this.bw2=bw2; this.bw3=bw3;
            this.voiced=voiced; this.noise=noise; this.amp=amp; this.dur=dur;
            this.a1=a1; this.a2=a2; this.a3=a3;
        }
    }

    // Vocales (F1, F2, F3 de referencia para voz masculina)
    static final Phone A = new Phone(730,1090,2440, 90,110,140, 1,0,1.0f,0.14f, 1.0f,0.6f,0.3f);
    static final Phone E = new Phone(530,1840,2480, 70,100,130, 1,0,0.95f,0.12f, 1.0f,0.6f,0.3f);
    static final Phone I = new Phone(270,2290,3010, 70,100,130, 1,0,0.85f,0.11f, 1.0f,0.7f,0.3f);
    static final Phone O = new Phone(570, 840,2410, 70, 90,130, 1,0,0.95f,0.13f, 1.0f,0.5f,0.3f);
    static final Phone U = new Phone(300, 870,2240, 70, 90,130, 1,0,0.8f,0.12f, 1.0f,0.5f,0.25f);

    // Oclusivas sordas: silencio + pequeña explosión
    static final Phone P = new Phone(400,1100,2200, 200,250,300, 0,0,0,     0.055f, 0,0,0);
    static final Phone T = new Phone(400,1600,2600, 200,250,300, 0,0,0,     0.055f, 0,0,0);
    static final Phone K = new Phone(400,1900,2400, 200,250,300, 0,0,0,     0.065f, 0,0,0);
    // Oclusivas sonoras: barra sonora débil durante el cierre
    static final Phone B = new Phone( 250, 900,2200, 100,150,200, 0.2f,0,0.35f,0.055f, 1,0.2f,0.1f);
    static final Phone D = new Phone( 250,1500,2500, 100,150,200, 0.2f,0,0.35f,0.055f, 1,0.2f,0.1f);
    static final Phone G = new Phone( 250,1800,2400, 100,150,200, 0.2f,0,0.35f,0.065f, 1,0.2f,0.1f);

    // Fricativas: solo ruido
    static final Phone S  = new Phone(5000,6500,8000, 800,900,1000, 0,0.75f,0.55f,0.10f, 0,0.8f,0.3f);
    static final Phone F  = new Phone(1500,4000,6000, 700,800, 900, 0,0.55f,0.40f,0.09f, 0,0.8f,0.3f);
    static final Phone X  = new Phone(1500,2500,3500, 700,800, 900, 0,0.65f,0.50f,0.10f, 0,0.8f,0.4f);
    static final Phone TH = new Phone(4000,5500,7000, 800,900,1000, 0,0.55f,0.40f,0.09f, 0,0.8f,0.3f);
    // Africada ch ≈ t + ʃ
    static final Phone CH = new Phone(1800,2800,3800, 700,800, 900, 0,0.6f,0.5f,0.10f, 0,0.8f,0.4f);

    // Nasales: formantes con F1 muy baja
    static final Phone M  = new Phone( 250,1100,2200, 110,140,170, 1,0,0.5f,0.07f, 1,0.4f,0.2f);
    static final Phone N  = new Phone( 250,1700,2600, 110,140,170, 1,0,0.5f,0.07f, 1,0.4f,0.2f);
    static final Phone NY = new Phone( 250,2100,2900, 110,140,170, 1,0,0.5f,0.08f, 1,0.4f,0.2f);

    // Líquidas
    static final Phone L  = new Phone( 360,1300,2700, 90,110,140, 1,0,0.7f,0.055f, 1,0.5f,0.3f);
    static final Phone R  = new Phone( 400,1300,1700, 90,110,140, 1,0,0.7f,0.035f, 1,0.5f,0.3f);
    static final Phone RR = new Phone( 500,1300,1700, 90,110,140, 1,0,0.7f,0.09f,  1,0.5f,0.3f);

    static final Phone SIL = new Phone(500,1500,2500, 200,250,300, 0,0,0, 0.045f, 0,0,0);

    // ------------------------------------------------------- fonemizar

    static List<Phone> phonemize(String raw) {
        List<Phone> out = new ArrayList<Phone>();
        if (raw == null) return out;

        String s = strip(raw);
        int i = 0;
        while (i < s.length()) {
            char c  = s.charAt(i);
            char n  = i+1 < s.length() ? s.charAt(i+1) : 0;
            char n2 = i+2 < s.length() ? s.charAt(i+2) : 0;

            if (c == ' ') { out.add(SIL); i++; continue; }

            // Digrafos
            if (c == 'c' && n == 'h') { out.add(CH); i += 2; continue; }
            if (c == 'l' && n == 'l') { out.add(L); out.add(E); i += 2; continue; }
            if (c == 'r' && n == 'r') { out.add(RR); i += 2; continue; }
            if (c == 'q' && n == 'u') { out.add(K); i += 2; continue; }
            if (c == 'g' && n == 'u' && (n2 == 'e' || n2 == 'i')) {
                out.add(G); i += 2; continue;
            }

            switch (c) {
                case 'a': out.add(A); break;
                case 'e': out.add(E); break;
                case 'i': out.add(I); break;
                case 'o': out.add(O); break;
                case 'u': out.add(U); break;
                case 'b': case 'v': out.add(B); break;
                case 'c':
                    if (n == 'e' || n == 'i') out.add(S);
                    else out.add(K);
                    break;
                case 'd': out.add(D); break;
                case 'f': out.add(F); break;
                case 'g':
                    if (n == 'e' || n == 'i') out.add(X);
                    else out.add(G);
                    break;
                case 'h': break; // muda
                case 'j': out.add(X); break;
                case 'k': out.add(K); break;
                case 'l': out.add(L); break;
                case 'm': out.add(M); break;
                case 'n': out.add(N); break;
                case 'p': out.add(P); break;
                case 'r': {
						char prev = i > 0 ? s.charAt(i-1) : 0;
						if (i == 0 || prev == 'n' || prev == 'l' || prev == 's')
							out.add(RR);
						else out.add(R);
						break;
					}
                case 's': out.add(S); break;
                case 't': out.add(T); break;
                case 'w': out.add(U); break;
                case 'x': out.add(K); out.add(S); break;
                case 'y': out.add(I); break;
                case 'z': out.add(TH); break;
					// dígitos y signos: se ignoran
            }
            i++;
        }
        return out;
    }

    private static String strip(String raw) {
        String n = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ");
    }

    // -------------------------------------------------------- sintetizar

    static float[] synthesize(String text) {
        return render(phonemize(text));
    }

    static float[] render(List<Phone> phones) {
        if (phones.isEmpty()) return new float[0];

        // Longitud total: suma + cola
        int total = SR / 8;
        for (Phone p : phones) total += (int)(p.dur * SR);

        float[] out = new float[total];
        int idx = 0;

        // Estado de los 3 resonadores (2 polos cada uno)
        float[] y1 = new float[2], y2 = new float[2], y3 = new float[2];
        double phase = 0;
        Random rnd = new Random(0x4D694E41L); // semilla fija = reproducible

        for (int pi = 0; pi < phones.size(); pi++) {
            Phone p = phones.get(pi);
            Phone nx = pi+1 < phones.size() ? phones.get(pi+1) : p;
            int n = (int)(p.dur * SR);

            // Constantes de interpolación (mezcla hacia el siguiente fonema)
            for (int i = 0; i < n; i++) {
                float t = (float)i / n;
                float blend = t > 0.35f ? (t - 0.35f) / 0.65f : 0f;

                float f1 = lerp(p.f1, nx.f1, blend);
                float f2 = lerp(p.f2, nx.f2, blend);
                float f3 = lerp(p.f3, nx.f3, blend);
                float bw1= lerp(p.bw1,nx.bw1,blend);
                float bw2= lerp(p.bw2,nx.bw2,blend);
                float bw3= lerp(p.bw3,nx.bw3,blend);
                float v  = lerp(p.voiced,nx.voiced,blend);
                float no = lerp(p.noise, nx.noise, blend);
                float am = lerp(p.amp, nx.amp, blend);
                float a1 = lerp(p.a1, nx.a1, blend);
                float a2 = lerp(p.a2, nx.a2, blend);
                float a3 = lerp(p.a3, nx.a3, blend);

                // Fuente glotal: tren de pulsos Rosenberg simplificado
                float f0 = F0 + (rnd.nextFloat() - 0.5f) * 4f; // jitter
                phase += f0 / SR;
                if (phase >= 1.0) phase -= 1.0;
                float pulse = 0f;
                double ph = phase;
                if (ph < 0.4) {
                    pulse = (float)(0.5 * (1 - Math.cos(Math.PI * ph / 0.4)));
                } else if (ph < 0.6) {
                    pulse = (float)Math.cos(Math.PI * (ph - 0.4) / 0.4);
                }
                // Derivada aproximada para dar brillo a las vocales
                pulse = pulse - 0.7f * (float)Math.cos(2 * Math.PI * ph);

                float src = pulse * v;

                // Ruido para fricativas
                if (no > 0.001f) {
                    src += (rnd.nextFloat() * 2f - 1f) * no;
                }
                src *= am;

                // Resonadores en paralelo (Klatt-style)
                float r1o = resonator(src, f1, bw1, y1);
                float r2o = resonator(src, f2, bw2, y2);
                float r3o = resonator(src, f3, bw3, y3);

                float sample = a1 * r1o + a2 * r2o + a3 * r3o;
                out[idx++] = sample;
            }
        }

        // Normalizar a ~0.9
        float max = 1e-6f;
        for (int i = 0; i < idx; i++) max = Math.max(max, Math.abs(out[i]));
        float g = 0.9f / max;
        for (int i = 0; i < idx; i++) out[i] *= g;

        // Fade-in / fade-out breves para evitar clics
        int fade = SR / 200;
        for (int i = 0; i < fade && i < idx; i++) {
            float k = (float)i / fade;
            out[i] *= k;
            out[idx-1-i] *= k;
        }
        return out;
    }

    /** Resonador de 2 polos: y[n] = x[n] + 2r cosθ·y[n-1] − r²·y[n-2] */
    private static float resonator(float x, float freq, float bw, float[] s) {
        double r  = Math.exp(-Math.PI * bw / SR);
        double th = 2 * Math.PI * freq / SR;
        float c1 = (float)(2 * r * Math.cos(th));
        float c2 = (float)(-r * r);
        float y  = x + c1 * s[0] + c2 * s[1];
        s[1] = s[0];
        s[0] = y;
        // Ganancia: normaliza por (1-r) para que un impulso unitario dé ~1
        return y * (float)(1 - r);
    }

    private static float lerp(float a, float b, float t) { return a + (b-a)*t; }
}
