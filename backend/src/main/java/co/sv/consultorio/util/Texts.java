package co.sv.consultorio.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Texts {

    private Texts() {
    }

    public static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** Indicativo sin signos. Vacío significa Colombia. */
    public static String countryCode(String s) {
        String d = digitsOnly(s);
        return d.isEmpty() ? "57" : d;
    }

    /** Teléfono local. Si el país es Colombia, quita un 57 o 0057 que hayan escrito en el número. */
    public static String localPhone(String s, String country) {
        String d = digitsOnly(s);
        if ("57".equals(countryCode(country))) {
            if (d.length() == 14 && d.startsWith("0057")) {
                d = d.substring(4);
            } else if (d.length() == 12 && d.startsWith("57")) {
                d = d.substring(2);
            }
        }
        return d;
    }

    /** Deja solo dígitos y quita el indicativo de Colombia (+57 o 0057) si viene incluido. */
    public static String normalizePhone(String s) {
        return localPhone(s, "57");
    }

    /** "3165550166" → "316 555 0166" */
    public static String formatPhone(String digits) {
        if (digits == null) {
            return "";
        }
        if (digits.length() == 10) {
            return digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6);
        }
        return digits;
    }

    /** Quita espacios sobrantes. */
    public static String cleanName(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    /** Minúsculas y sin tildes, para buscar. */
    public static String searchKey(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
