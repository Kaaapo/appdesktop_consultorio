package co.sv.consultorio.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Texts {

    private Texts() {
    }

    public static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** Deja solo dígitos y quita el indicativo de Colombia (+57 o 0057) si viene incluido. */
    public static String normalizePhone(String s) {
        String d = digitsOnly(s);
        if (d.length() == 14 && d.startsWith("0057")) {
            d = d.substring(4);
        } else if (d.length() == 12 && d.startsWith("57")) {
            d = d.substring(2);
        }
        return d;
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
