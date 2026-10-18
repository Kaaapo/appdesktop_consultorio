package co.sv.consultorio.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Preferencias del programa guardadas en la base de datos. */
public record AppSettings(
        ThemeMode theme,
        FontSize fontSize,
        boolean popupToday,
        boolean popupWeek,
        boolean popupMonth,
        int reminderDays,
        List<LocalTime> slotHours,
        Set<DayOfWeek> workDays,
        LocalDate lastPopupDate) {

    public enum ThemeMode { LIGHT, DARK, AUTO }

    public enum FontSize {
        NORMAL(1.0), LARGE(1.12), XLARGE(1.25);

        private final double scale;

        FontSize(double scale) {
            this.scale = scale;
        }

        public double scale() {
            return scale;
        }
    }

    public boolean anyPopup() {
        return popupToday || popupWeek || popupMonth;
    }

    public AppSettings withTheme(ThemeMode v) {
        return new AppSettings(v, fontSize, popupToday, popupWeek, popupMonth, reminderDays, slotHours, workDays, lastPopupDate);
    }

    public AppSettings withFontSize(FontSize v) {
        return new AppSettings(theme, v, popupToday, popupWeek, popupMonth, reminderDays, slotHours, workDays, lastPopupDate);
    }

    public AppSettings withPopups(boolean today, boolean week, boolean month) {
        return new AppSettings(theme, fontSize, today, week, month, reminderDays, slotHours, workDays, lastPopupDate);
    }

    public AppSettings withReminderDays(int v) {
        return new AppSettings(theme, fontSize, popupToday, popupWeek, popupMonth, v, slotHours, workDays, lastPopupDate);
    }

    public AppSettings withLastPopupDate(LocalDate v) {
        return new AppSettings(theme, fontSize, popupToday, popupWeek, popupMonth, reminderDays, slotHours, workDays, v);
    }
}
