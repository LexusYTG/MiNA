package com.Lexus2026.MiNA;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

class CommandRouter {

    static class Response {
        final String text;
        final String action; // null = solo hablar

        Response(String text) { this(text, null); }
        Response(String text, String action) {
            this.text = text;
            this.action = action;
        }
    }

    Response route(String raw) {
        if (raw == null) return new Response("No te entendí");
        String q = raw.toLowerCase(Locale.getDefault()).trim();
        if (q.isEmpty()) return new Response("No te entendí");

        if (hit(q, "hola", "buenas", "hey", "buenos días", "buenas tardes", "buenas noches"))
            return new Response("Hola, soy MiNA. ¿En qué te ayudo?");

        if (hit(q, "qué hora", "que hora", "la hora"))
            return new Response("Son las "
								+ new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));

        if (hit(q, "qué día", "que dia", "fecha", "qué fecha"))
            return new Response("Hoy es "
								+ new SimpleDateFormat("EEEE d 'de' MMMM",
													   new Locale("es", "ES")).format(new Date()));

        if (hit(q, "cómo te llamas", "como te llamas", "tu nombre"))
            return new Response("Me llamo MiNA");

        if (hit(q, "gracias"))
            return new Response("De nada");

        if (hit(q, "adiós", "adios", "chao", "hasta luego", "cierra"))
            return new Response("Hasta luego", "finish");

        return new Response("Todavía no sé responder a eso.");
    }

    private boolean hit(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }
}
