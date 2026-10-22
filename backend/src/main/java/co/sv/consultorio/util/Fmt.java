package co.sv.consultorio.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Formatos de fecha y hora en español de Colombia (es-CO), escritos a mano para que sean estables. */
public final class Fmt {

    public static final Locale LOCALE = Locale.forLanguageTag("es-CO");
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final String[] MONTHS_SHORT = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago",
            "sep", "oct", "nov", "dic"};
    private static final String[] DOWS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final String[] DOWS_SHORT = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    private Fmt() {
    }

    public static String month(LocalDate d) {
        return MONTHS[d.getMonthValue() - 1];
    }

    public static String monthName(int month) {
        return MONTHS[month - 1];
    }

    public static String monthShort(LocalDate d) {
        return MONTHS_SHORT[d.getMonthValue() - 1];
    }

    public static String dow(LocalDate d) {
        return DOWS[d.getDayOfWeek().getValue() - 1];
    }

    /** "Mié" */
    public static String dowShort(LocalDate d) {
        return DOWS_SHORT[d.getDayOfWeek().getValue() - 1];
    }

    /** "13 oct". El año se agrega en {@link #shortDate(LocalDate, LocalDate)}. */
    public static String shortDate(LocalDate d) {
        return d.getDayOfMonth() + " " + monthShort(d);
    }

    /** "13 oct 2026". El año va siempre; {@code today} se conserva para las llamadas que ya lo pasaban. */
    public static String shortDate(LocalDate d, LocalDate today) {
        return shortDate(d) + " " + (today == null ? d.getYear() : d.getYear());
    }

    /** "13 de noviembre de 2026" */
    public static String longDate(LocalDate d) {
        return d.getDayOfMonth() + " de " + month(d) + " de " + d.getYear();
    }

    /** Igual que {@link #longDate}: día, mes y año. */
    public static String longDateYear(LocalDate d) {
        return longDate(d);
    }

    /** "viernes 16 de octubre de 2026" */
    public static String longDateDow(LocalDate d) {
        return dow(d) + " " + longDate(d);
    }

    /** "Martes, 13 de octubre de 2026" */
    public static String headerDate(LocalDate d) {
        return capitalize(dow(d)) + ", " + longDateYear(d);
    }

    /** "9:00" */
    public static String time(LocalTime t) {
        return t.getHour() + ":" + String.format("%02d", t.getMinute());
    }

    public static String time(LocalDateTime dt) {
        return time(dt.toLocalTime());
    }

    /** "10:12 a. m." */
    public static String time12(LocalTime t) {
        int h = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return h + ":" + String.format("%02d", t.getMinute()) + (t.getHour() < 12 ? " a. m." : " p. m.");
    }

    public static String time12(LocalDateTime dt) {
        return time12(dt.toLocalTime());
    }

    /** Fecha para el mensaje de WhatsApp: "viernes 16 de octubre de 2026 a las 10:30 a. m.". */
    public static String messageDate(LocalDateTime dt) {
        return longDateDow(dt.toLocalDate()) + (dt.getHour() % 12 == 1 ? " a la " : " a las ") + time12(dt);
    }

    /** "viernes 16 de octubre de 2026, 10:30 a. m." */
    public static String appointmentLong(LocalDateTime dt) {
        return longDateDow(dt.toLocalDate()) + ", " + time12(dt);
    }

    /** Chip de cita: "Hoy 9:00 a. m.", "Mié 14 · 3:30 p. m." o "15 nov · 9:00 a. m.". */
    public static String appointmentChip(LocalDateTime dt, LocalDate today) {
        LocalDate d = dt.toLocalDate();
        long days = ChronoUnit.DAYS.between(today, d);
        if (days == 0) {
            return "Hoy " + time12(dt);
        }
        if (days > 0 && days < 7) {
            return dowShort(d) + " " + shortDate(d) + " " + d.getYear() + " · " + time12(dt);
        }
        return shortDate(d, today) + " · " + time12(dt);
    }

    /** Día corto para listas: "Hoy", "Mié 14" o "15 nov". */
    public static String dayShort(LocalDate d, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, d);
        if (days == 0) {
            return "Hoy";
        }
        if (days > 0 && days < 7) {
            return dowShort(d) + " " + shortDate(d) + " " + d.getYear();
        }
        return shortDate(d, today);
    }

    /** "hoy 9:00 a. m." o "mié 14 oct, 10:30 a. m." para usar dentro de frases. */
    public static String appointmentInline(LocalDateTime dt, LocalDate today) {
        LocalDate d = dt.toLocalDate();
        if (d.equals(today)) {
            return "hoy " + time12(dt);
        }
        return dowShort(d).toLowerCase(LOCALE) + " " + shortDate(d, today) + ", " + time12(dt);
    }

    /** "1 cita", "3 citas" */
    public static String count(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(LOCALE) + s.substring(1);
    }
}
