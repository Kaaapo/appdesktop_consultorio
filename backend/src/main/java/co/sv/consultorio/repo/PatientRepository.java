package co.sv.consultorio.repo;

import co.sv.consultorio.db.Database;
import co.sv.consultorio.db.DbTypes;
import co.sv.consultorio.domain.Patient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class PatientRepository {

    private final Database db;

    public PatientRepository(Database db) {
        this.db = db;
    }

    public long insert(Patient p) {
        return db.insert("""
                        INSERT INTO patient (full_name, document, phone, country_code, process_id, birth_date, registered_at, active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                p.fullName(), p.document(), p.phone(), p.countryCode(), p.processId(), p.birthDate(), p.registeredAt(), p.active());
    }

    public void update(Patient p) {
        db.update("""
                        UPDATE patient SET full_name = ?, document = ?, phone = ?, country_code = ?, process_id = ?, birth_date = ?, active = ?
                        WHERE id = ?""",
                p.fullName(), p.document(), p.phone(), p.countryCode(), p.processId(), p.birthDate(), p.active(), p.id());
    }

    public void setActive(long id, boolean active) {
        db.update("UPDATE patient SET active = ? WHERE id = ?", active, id);
    }

    public Patient findById(long id) {
        return db.queryOne("SELECT * FROM patient WHERE id = ?", PatientRepository::map, id);
    }

    public Patient findByDocument(String document) {
        return db.queryOne("SELECT * FROM patient WHERE document = ?", PatientRepository::map, document);
    }

    /** Todos los pacientes, más recientes primero. */
    public List<Patient> findAll() {
        return db.query("SELECT * FROM patient ORDER BY registered_at DESC, id DESC", PatientRepository::map);
    }

    public int countActive() {
        return db.queryOne("SELECT COUNT(*) FROM patient WHERE active = 1", rs -> rs.getInt(1));
    }

    static Patient map(ResultSet rs) throws SQLException {
        return new Patient(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("document"),
                rs.getString("phone"),
                country(rs),
                processId(rs),
                DbTypes.date(rs.getString("birth_date")),
                DbTypes.dateTime(rs.getString("registered_at")),
                rs.getInt("active") == 1);
    }

    private static Long processId(ResultSet rs) throws SQLException {
        long id = rs.getLong("process_id");
        return rs.wasNull() ? null : id;
    }

    private static String country(ResultSet rs) throws SQLException {
        try {
            String code = rs.getString("country_code");
            return code == null || code.isBlank() ? "57" : code;
        } catch (SQLException e) {
            return "57";
        }
    }
}
