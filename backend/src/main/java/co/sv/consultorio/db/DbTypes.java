package co.sv.consultorio.db;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

/** Fechas guardadas como texto ISO en hora local de Bogotá: "2026-10-13" y "2026-10-13T09:00:00". */
public final class DbTypes {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private DbTypes() {
    }

    public static String format(Temporal t) {
        if (t instanceof LocalDateTime dt) {
            return DATE_TIME.format(dt.truncatedTo(ChronoUnit.SECONDS));
        }
        return t.toString();
    }

    public static LocalDate date(String s) {
        return s == null || s.isEmpty() ? null : LocalDate.parse(s);
    }

    public static LocalDateTime dateTime(String s) {
        return s == null || s.isEmpty() ? null : LocalDateTime.parse(s);
    }
}
