package co.sv.consultorio.app.ui;

import co.sv.consultorio.domain.AppSettings;
import javafx.scene.Scene;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tema claro/oscuro y tamaño de letra. Genera la hoja de estilos final: paleta en {@code .root}
 * + {@code app.css} con todos los px multiplicados por la escala (equivale al "zoom" de los mockups).
 */
public final class Theme {

    private static final Map<String, String> LIGHT = palette(
            "bg", "#F7F1ED", "panel", "#FFF9F6", "soft", "#F6E7E1", "line", "#E8D4CC", "line2", "#F3E3DC",
            "ink", "#3A2A2E", "ink2", "#5C4548", "mute", "#8C726C", "fb", "#C9A89F", "fbg", "#FFF9F6",
            "btn", "#7A4A56", "btnink", "#FFF9F6", "hover", "#F8EBE6", "sel", "#F3E0D8", "busy", "#EAD3CA",
            "hero", "#F6E4DC", "heroink", "#3A2A2E", "herofield", "#FFF8F5", "herofb", "#E4C4B8", "heromute", "#8D6B64",
            "pendbg", "#F3E9D2", "pend", "#6B4A0E", "avisbg", "#E4E7EE", "avis", "#2F3E66", "agenbg", "#DDEBE1",
            "agen", "#21593A", "norbg", "#F1DEDC", "nor", "#7C2A24", "compbg", "#ECEBE7", "comp", "#55555A",
            "focus", "#7A4A56");

    private static final Map<String, String> DARK = palette(
            "bg", "#0A0A0B", "panel", "#141416", "soft", "#1B1B1E", "line", "#2A2A2E", "line2", "#212124",
            "ink", "#F5F4F1", "ink2", "#D6D5D1", "mute", "#A3A3A8", "fb", "#5A5A60", "fbg", "#0F0F11",
            "btn", "#F5F4F1", "btnink", "#0B0B0C", "hover", "#1F1F23", "sel", "#222226", "busy", "#2B2B30",
            "hero", "#1F1F23", "heroink", "#F5F4F1", "herofield", "#111113", "herofb", "#4A4A50", "heromute", "#A9A8A4",
            "pendbg", "#3A3018", "pend", "#E9D08F", "avisbg", "#232A3D", "avis", "#BFC9E6", "agenbg", "#173025",
            "agen", "#9ED3B3", "norbg", "#3E2220", "nor", "#EFB9B3", "compbg", "#26262A", "comp", "#B8B7B2",
            "focus", "#F5F4F1");

    private static final Pattern PX = Pattern.compile("(-?\\d*\\.?\\d+)px");

    private static String baseCss;

    private Theme() {
    }

    /** ¿El tema elegido se ve oscuro? (Automático sigue el modo de Windows). */
    public static boolean isDark(AppSettings.ThemeMode mode) {
        return switch (mode) {
            case DARK -> true;
            case LIGHT -> false;
            case AUTO -> WindowsTheme.isDark();
        };
    }

    public static void apply(Scene scene, boolean dark, double scale) {
        Ui.setScale(scale);
        String css = buildCss(dark, scale);
        String uri = "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        scene.getStylesheets().setAll(uri);
    }

    static String buildCss(boolean dark, double scale) {
        StringBuilder sb = new StringBuilder(".root {\n");
        (dark ? DARK : LIGHT).forEach((k, v) -> sb.append("    -c-").append(k).append(": ").append(v).append(";\n"));
        sb.append("}\n");
        sb.append(base());
        return scale == 1.0 ? sb.toString() : scalePx(sb.toString(), scale);
    }

    static String scalePx(String css, double scale) {
        Matcher m = PX.matcher(css);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            double v = Double.parseDouble(m.group(1)) * scale;
            m.appendReplacement(out, String.format(Locale.ROOT, "%.2fpx", v));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static synchronized String base() {
        if (baseCss == null) {
            try (InputStream in = Theme.class.getResourceAsStream("/co/sv/consultorio/app/css/app.css")) {
                if (in == null) {
                    throw new IllegalStateException("Falta app.css");
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                baseCss = sb.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return baseCss;
    }

    private static Map<String, String> palette(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
