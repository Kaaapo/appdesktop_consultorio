package co.sv.consultorio.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);

    @TempDir
    Path tmp;

    @Test
    void errorInsideTransactionRollsBack() {
        try (Database db = Database.openInMemory()) {
            db.execute("CREATE TABLE t (x INTEGER)");
            assertThrows(AssertionError.class, () -> db.inTransaction(() -> {
                db.update("INSERT INTO t VALUES (1)");
                throw new AssertionError("falla");
            }));
            int rows = db.queryOne("SELECT COUNT(*) FROM t", rs -> rs.getInt(1));
            assertEquals(0, rows);
        }
    }

    @Test
    void rotationKeepsDatedCopiesAndIgnoresOtherFiles() throws Exception {
        Path backups = Files.createDirectories(tmp.resolve("backups"));
        Files.writeString(backups.resolve("consultorio-manual.db"), "copia hecha a mano");
        try (Database db = Database.openFile(tmp.resolve("consultorio.db"))) {
            for (int i = 0; i < 5; i++) {
                db.dailyBackup(DAY.plusDays(i), 3);
            }
        }
        assertEquals(List.of("consultorio-2026-10-15.db", "consultorio-2026-10-16.db", "consultorio-2026-10-17.db",
                "consultorio-manual.db"), names(backups));
    }

    @Test
    void interruptedCopyIsRetried() throws Exception {
        Path backups = Files.createDirectories(tmp.resolve("backups"));
        Files.writeString(backups.resolve("consultorio-2026-10-13.db.tmp"), "copia a medias");
        try (Database db = Database.openFile(tmp.resolve("consultorio.db"))) {
            db.dailyBackup(DAY, 3);
        }
        assertEquals(List.of("consultorio-2026-10-13.db"), names(backups));
        try (Database copy = Database.openFile(backups.resolve("consultorio-2026-10-13.db"))) {
            assertEquals(Migrations.latestVersion(), copy.userVersion());
        }
    }

    @Test
    void failedCopyDoesNotStopTheProgram() throws Exception {
        Files.writeString(tmp.resolve("backups"), "no es una carpeta");
        try (Database db = Database.openFile(tmp.resolve("consultorio.db"))) {
            assertDoesNotThrow(() -> db.dailyBackup(DAY, 3));
            assertEquals(Migrations.latestVersion(), db.userVersion());
        }
        assertFalse(Files.isDirectory(tmp.resolve("backups")));
        assertTrue(Files.exists(tmp.resolve("consultorio.db")));
    }

    private static List<String> names(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }
}
