package co.sv.consultorio.service;

import co.sv.consultorio.TestEnv;
import co.sv.consultorio.db.Database;
import co.sv.consultorio.domain.AppSettings;
import co.sv.consultorio.domain.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthAndSettingsTest {

    private final TestEnv env = new TestEnv();

    @TempDir
    Path tmp;

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void firstRunSetupThenLoginAndChangePassword() {
        AuthService auth = env.s.auth();
        assertFalse(auth.isConfigured());
        assertThrows(ValidationException.class, () -> auth.setupPassword("123", "123"));
        assertThrows(ValidationException.class, () -> auth.setupPassword("secreto1", "secreto2"));
        auth.setupPassword("secreto1", "secreto1");
        assertTrue(auth.isConfigured());
        assertTrue(auth.login("secreto1"));
        assertFalse(auth.login("otra"));

        ValidationException e = assertThrows(ValidationException.class,
                () -> auth.changePassword("mala", "nueva123", "nueva123"));
        assertTrue(e.fieldErrors().containsKey("current"));
        auth.changePassword("secreto1", "nueva123", "nueva123");
        assertTrue(auth.login("nueva123"));
        assertFalse(auth.login("secreto1"));
    }

    @Test
    void settingsDefaultsAndPersistence() {
        AppSettings s = env.s.settings().get();
        assertEquals(AppSettings.ThemeMode.LIGHT, s.theme());
        assertEquals(7, s.reminderDays());
        assertEquals(SettingsService.WEEKDAY_SLOTS.size(), s.slotHours().size());
        assertTrue(s.popupToday() && s.popupWeek() && s.popupMonth());

        env.s.settings().save(s.withTheme(AppSettings.ThemeMode.DARK).withFontSize(AppSettings.FontSize.XLARGE)
                .withPopups(true, false, true).withReminderDays(30).withLastPopupDate(TestEnv.TODAY));
        AppSettings reloaded = new Services(env.db, env.s.time()).settings().get();
        assertEquals(AppSettings.ThemeMode.DARK, reloaded.theme());
        assertEquals(AppSettings.FontSize.XLARGE, reloaded.fontSize());
        assertFalse(reloaded.popupWeek());
        assertEquals(30, reloaded.reminderDays());
        assertEquals(TestEnv.TODAY, reloaded.lastPopupDate());
        assertThrows(ValidationException.class, () -> env.s.settings().save(s.withReminderDays(0)));
    }

    @Test
    void fileDatabaseMigratesAndBacksUp() throws Exception {
        Path file = tmp.resolve("consultorio.db");
        try (Database db = Database.openFile(file)) {
            assertEquals(2, db.userVersion());
            db.dailyBackup(TestEnv.TODAY, 3);
        }
        assertTrue(Files.exists(tmp.resolve("backups").resolve("consultorio-2026-10-13.db")));
        try (Database db = Database.openFile(file)) {
            assertEquals(2, db.userVersion());
        }
    }
}
