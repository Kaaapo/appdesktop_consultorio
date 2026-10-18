package co.sv.consultorio.repo;

import co.sv.consultorio.db.Database;
import co.sv.consultorio.domain.Process;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessRepository {

    private final Database db;

    public ProcessRepository(Database db) {
        this.db = db;
    }

    public List<Process> findAll() {
        return db.query("SELECT * FROM process ORDER BY name COLLATE NOCASE", ProcessRepository::map);
    }

    public Process findById(long id) {
        return db.queryOne("SELECT * FROM process WHERE id = ?", ProcessRepository::map, id);
    }

    public Process findByName(String name) {
        return db.queryOne("SELECT * FROM process WHERE name = ? COLLATE NOCASE", ProcessRepository::map, name);
    }

    public long insert(String name, int months, String template, LocalDateTime now) {
        return db.insert("INSERT INTO process (name, months_to_second, message_template, created_at) VALUES (?, ?, ?, ?)",
                name, months, template, now);
    }

    public void update(long id, String name, int months, String template) {
        db.update("UPDATE process SET name = ?, months_to_second = ?, message_template = ? WHERE id = ?",
                name, months, template, id);
    }

    /** Pacientes activos por proceso. */
    public Map<Long, Integer> activePatientCounts() {
        Map<Long, Integer> out = new HashMap<>();
        db.query("SELECT process_id, COUNT(*) FROM patient WHERE active = 1 GROUP BY process_id",
                rs -> out.put(rs.getLong(1), rs.getInt(2)));
        return out;
    }

    private static Process map(ResultSet rs) throws SQLException {
        return new Process(rs.getLong("id"), rs.getString("name"), rs.getInt("months_to_second"),
                rs.getString("message_template"));
    }
}
