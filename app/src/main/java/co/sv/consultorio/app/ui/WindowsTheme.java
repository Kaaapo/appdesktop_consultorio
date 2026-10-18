package co.sv.consultorio.app.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Lee si Windows está en modo oscuro (para el tema "Automático"). */
final class WindowsTheme {

    private WindowsTheme() {
    }

    static boolean isDark() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line);
                }
            }
            p.waitFor(3, TimeUnit.SECONDS);
            return out.toString().contains("0x0");
        } catch (Exception e) {
            return false;
        }
    }
}
