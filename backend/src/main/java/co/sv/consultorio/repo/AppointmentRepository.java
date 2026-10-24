package co.sv.consultorio.repo;

import co.sv.consultorio.db.Database;
import co.sv.consultorio.db.DbTypes;
import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.WaStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppointmentRepository {

    private final Database db;

    public AppointmentRepository(Database db) {
        this.db = db;
    }

    public long insert(long patientId, LocalDateTime startsAt, AppointmentType type, LocalDateTime now, Long processId) {
        return db.insert("""
                        INSERT INTO appointment (patient_id, starts_at, type, process_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)""",
                patientId, startsAt, type, processId, now, now);
    }

    /** Guarda todos los campos de la cita (incluye el aviso). */
    public void update(Appointment a, LocalDateTime now) {
        db.update("""
                        UPDATE appointment SET starts_at = ?, type = ?, notified_at = ?, notified_text = ?, wa_status = ?, process_id = ?, updated_at = ?
                        WHERE id = ?""",
                a.startsAt(), a.type(), a.notifiedAt(), a.notifiedText(), a.waStatus(), a.processId(), now, a.id());
    }

    public void delete(long id) {
        db.update("DELETE FROM appointment WHERE id = ?", id);
    }

    /** Marca el aviso si todavía no se ha enviado, o si quedó como "no respondió". */
    public boolean markNotified(long id, LocalDateTime at, String text) {
        return db.update("""
                        UPDATE appointment SET notified_at = ?, notified_text = ?, wa_status = 'AVISADO', updated_at = ?
                        WHERE id = ? AND wa_status IN ('NONE', 'NO_RESPONDIO')""",
                at, text, at, id) == 1;
    }

    /** Registra si la persona contestó. Solo vale cuando el estado actual es AVISADO. */
    public boolean markOutcome(long id, WaStatus status, LocalDateTime now) {
        return db.update("UPDATE appointment SET wa_status = ?, updated_at = ? WHERE id = ? AND wa_status = 'AVISADO'",
                status, now, id) == 1;
    }

    /** Cuántas citas (sin controles) tiene cada paciente. */
    public Map<Long, Integer> citaCountByPatient() {
        Map<Long, Integer> out = new HashMap<>();
        db.query("SELECT patient_id, COUNT(*) FROM appointment WHERE type = 'CITA' GROUP BY patient_id",
                rs -> out.put(rs.getLong(1), rs.getInt(2)));
        return out;
    }

    /** Cuántos controles tiene cada paciente. No entran en el número de cita. */
    public Map<Long, Integer> controlCountByPatient() {
        Map<Long, Integer> out = new HashMap<>();
        db.query("SELECT patient_id, COUNT(*) FROM appointment WHERE type = 'CONTROL' GROUP BY patient_id",
                rs -> out.put(rs.getLong(1), rs.getInt(2)));
        return out;
    }

    /** Fecha de la última cita de seguimiento de cada paciente (no cuenta los controles). */
    public Map<Long, LocalDate> lastCitaByPatient() {
        Map<Long, LocalDate> out = new HashMap<>();
        db.query("SELECT patient_id, MAX(starts_at) FROM appointment WHERE type = 'CITA' GROUP BY patient_id",
                rs -> out.put(rs.getLong(1), DbTypes.dateTime(rs.getString(2)).toLocalDate()));
        return out;
    }

    /**
     * Número de cada cita de seguimiento, en orden de fecha. Los controles no entran.
     * Clave = id de la cita.
     */
    public Map<Long, Integer> citaNumberByAppointment() {
        Map<Long, Integer> out = new HashMap<>();
        Map<Long, Integer> seq = new HashMap<>();
        db.query("SELECT id, patient_id FROM appointment WHERE type = 'CITA' ORDER BY starts_at, id", rs -> {
            long patientId = rs.getLong(2);
            int n = seq.merge(patientId, 1, Integer::sum);
            out.put(rs.getLong(1), n);
            return null;
        });
        return out;
    }

    public Appointment findById(long id) {
        return db.queryOne("SELECT * FROM appointment WHERE id = ?", AppointmentRepository::map, id);
    }

    /** Cita vigente: la más próxima desde el inicio de {@code today}. */
    public Appointment findCurrent(long patientId, LocalDate today) {
        return db.queryOne("SELECT * FROM appointment WHERE patient_id = ? AND starts_at >= ? ORDER BY starts_at LIMIT 1",
                AppointmentRepository::map, patientId, today.atStartOfDay());
    }

    /** Cita vigente de cada paciente (clave = id del paciente). */
    public Map<Long, Appointment> findCurrentByPatient(LocalDate today) {
        Map<Long, Appointment> out = new HashMap<>();
        for (Appointment a : db.query("SELECT * FROM appointment WHERE starts_at >= ? ORDER BY starts_at DESC",
                AppointmentRepository::map, today.atStartOfDay())) {
            out.put(a.patientId(), a);
        }
        return out;
    }

    /** Citas de pacientes activos entre [from, to). */
    public List<Appointment> findBetween(LocalDateTime from, LocalDateTime to) {
        return db.query("""
                        SELECT a.* FROM appointment a JOIN patient p ON p.id = a.patient_id
                        WHERE p.active = 1 AND a.starts_at >= ? AND a.starts_at < ?
                        ORDER BY a.starts_at""",
                AppointmentRepository::map, from, to);
    }

    public List<Appointment> findByPatient(long patientId) {
        return db.query("SELECT * FROM appointment WHERE patient_id = ? ORDER BY starts_at",
                AppointmentRepository::map, patientId);
    }

    public List<Appointment> findAllOrdered() {
        return db.query("SELECT * FROM appointment ORDER BY starts_at", AppointmentRepository::map);
    }

    /** Media hora siguiente que tapa una cita (no un control). */
    public static final int GAP_MINUTES = 30;

    /**
     * La hora ya no se puede usar. Una cita tapa su hora y la media hora siguiente;
     * un control solo tapa la hora exacta. Las horas anteriores quedan libres.
     */
    public boolean slotTaken(LocalDateTime startsAt, long excludeAppointmentId) {
        return db.queryOne("""
                        SELECT COUNT(*) FROM appointment a JOIN patient p ON p.id = a.patient_id
                        WHERE p.active = 1 AND a.id <> ?
                          AND (
                            a.starts_at = ?
                            OR (a.type = 'CITA' AND a.starts_at = ?)
                          )""",
                rs -> rs.getInt(1), excludeAppointmentId, startsAt,
                startsAt.minusMinutes(GAP_MINUTES)) > 0;
    }

    private static Long processId(ResultSet rs) throws SQLException {
        long id = rs.getLong("process_id");
        return rs.wasNull() ? null : id;
    }

    private static WaStatus wa(String raw) {
        if (raw == null || raw.isBlank()) {
            return WaStatus.NONE;
        }
        return WaStatus.valueOf(raw);
    }

    static Appointment map(ResultSet rs) throws SQLException {
        return new Appointment(
                rs.getLong("id"),
                rs.getLong("patient_id"),
                DbTypes.dateTime(rs.getString("starts_at")),
                AppointmentType.valueOf(rs.getString("type")),
                DbTypes.dateTime(rs.getString("notified_at")),
                rs.getString("notified_text"),
                wa(rs.getString("wa_status")),
                DbTypes.dateTime(rs.getString("created_at")),
                processId(rs));
    }
}
