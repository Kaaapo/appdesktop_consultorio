package co.sv.consultorio.repo;

import co.sv.consultorio.db.Database;

import java.time.LocalDateTime;

public class AdminRepository {

    private final Database db;

    public AdminRepository(Database db) {
        this.db = db;
    }

    /** Hash de la contraseña del Administrador, o null si aún no se ha configurado. */
    public String passwordHash() {
        return db.queryOne("SELECT password_hash FROM admin WHERE id = 1", rs -> rs.getString(1));
    }

    public void savePasswordHash(String username, String hash, LocalDateTime now) {
        db.update("""
                        INSERT INTO admin (id, username, password_hash, updated_at) VALUES (1, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET password_hash = excluded.password_hash, updated_at = excluded.updated_at""",
                username, hash, now);
    }
}
