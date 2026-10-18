package co.sv.consultorio.service;

import co.sv.consultorio.domain.AppSettings;
import co.sv.consultorio.domain.AppSettings.FontSize;
import co.sv.consultorio.domain.AppSettings.ThemeMode;
import co.sv.consultorio.domain.ValidationException;
import co.sv.consultorio.repo.SettingsRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SettingsService {

    /** Lunes a viernes: 9:00 a 12:00 y 14:00 a 18:00, cada media hora. */
    public static final List<LocalTime> WEEKDAY_SLOTS = hours(9, 0, 12, 0).and(hours(14, 0, 18, 0));
    /** Sábados: 9:00 a 13:00, cada media hora. */
    public static final List<LocalTime> SATURDAY_SLOTS = hours(9, 0, 13, 0).list();
    public static final List<LocalTime> DEFAULT_SLOTS = WEEKDAY_SLOTS;
    public static final Set<DayOfWeek> DEFAULT_WORK_DAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
    public static final int DEFAULT_REMINDER_DAYS = 7;

    private final SettingsRepository repo;
    private AppSettings cache;

    public SettingsService(SettingsRepository repo) {
        this.repo = repo;
    }

    /** Horas en las que se puede agendar, según el día. El domingo no hay consulta. */
    public List<LocalTime> slotsFor(DayOfWeek day) {
        if (day == DayOfWeek.SATURDAY) {
            return SATURDAY_SLOTS;
        }
        if (day == DayOfWeek.SUNDAY) {
            return List.of();
        }
        return WEEKDAY_SLOTS;
    }

    public AppSettings get() {
        if (cache == null) {
            cache = load(repo.all());
        }
        return cache;
    }

    public void save(AppSettings s) {
        if (s.reminderDays() < 1 || s.reminderDays() > 90) {
            throw new ValidationException(Map.of("reminderDays", "Elige entre 1 y 90 días de anticipación."));
        }
        repo.put("theme", s.theme().name());
        repo.put("font_size", s.fontSize().name());
        repo.put("popup_today", String.valueOf(s.popupToday()));
        repo.put("popup_week", String.valueOf(s.popupWeek()));
        repo.put("popup_month", String.valueOf(s.popupMonth()));
        repo.put("reminder_days", String.valueOf(s.reminderDays()));
        repo.put("slot_hours", s.slotHours().stream().map(LocalTime::toString).collect(Collectors.joining(",")));
        repo.put("work_days", s.workDays().stream().map(d -> String.valueOf(d.getValue())).collect(Collectors.joining(",")));
        repo.put("last_popup_date", s.lastPopupDate() == null ? "" : s.lastPopupDate().toString());
        cache = s;
    }

    private static AppSettings load(Map<String, String> m) {
        return new AppSettings(
                parseEnum(ThemeMode.class, m.get("theme"), ThemeMode.LIGHT),
                parseEnum(FontSize.class, m.get("font_size"), FontSize.NORMAL),
                bool(m.get("popup_today"), true),
                bool(m.get("popup_week"), true),
                bool(m.get("popup_month"), true),
                integer(m.get("reminder_days"), DEFAULT_REMINDER_DAYS),
                slots(m.get("slot_hours")),
                workDays(m.get("work_days")),
                m.get("last_popup_date") == null || m.get("last_popup_date").isEmpty()
                        ? null : LocalDate.parse(m.get("last_popup_date")));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String v, E def) {
        try {
            return v == null ? def : Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static boolean bool(String v, boolean def) {
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private static int integer(String v, int def) {
        try {
            return v == null ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static HalfHours hours(int fromHour, int fromMinute, int toHour, int toMinute) {
        return new HalfHours(fromHour, fromMinute, toHour, toMinute);
    }

    /** Intervalo inclusive de medias horas. */
    private record HalfHours(int fromHour, int fromMinute, int toHour, int toMinute) {
        List<LocalTime> list() {
            List<LocalTime> out = new java.util.ArrayList<>();
            LocalTime t = LocalTime.of(fromHour, fromMinute);
            LocalTime end = LocalTime.of(toHour, toMinute);
            while (!t.isAfter(end)) {
                out.add(t);
                t = t.plusMinutes(30);
            }
            return List.copyOf(out);
        }

        List<LocalTime> and(HalfHours other) {
            List<LocalTime> out = new java.util.ArrayList<>(list());
            out.addAll(other.list());
            return List.copyOf(out);
        }
    }

    private static List<LocalTime> slots(String v) {
        if (v == null || v.isBlank()) {
            return DEFAULT_SLOTS;
        }
        return Arrays.stream(v.split(",")).map(String::trim).map(LocalTime::parse).sorted().toList();
    }

    private static Set<DayOfWeek> workDays(String v) {
        if (v == null || v.isBlank()) {
            return DEFAULT_WORK_DAYS;
        }
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        for (String s : v.split(",")) {
            out.add(DayOfWeek.of(Integer.parseInt(s.trim())));
        }
        out.remove(DayOfWeek.SUNDAY);
        return out.isEmpty() ? DEFAULT_WORK_DAYS : out;
    }
}
