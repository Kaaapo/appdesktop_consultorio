package co.sv.consultorio.domain;

import co.sv.consultorio.util.Fmt;

import java.time.LocalDate;

/** Siguiente cita que le tocaría a un paciente que hoy no tiene una cita de seguimiento asignada. */
public record NextStep(AppointmentType type, LocalDate date, Kind kind) {

    public enum Kind { NONE, SUGGESTED, OVERDUE }

    /** {@code nextNumber} es el número que tendría la próxima cita (las que ya tiene + 1). */
    public String note(int nextNumber) {
        return switch (kind) {
            case NONE -> "Sin citas";
            case SUGGESTED -> "Cita " + nextNumber + " sugerida: " + Fmt.shortDate(date);
            case OVERDUE -> "Cita " + nextNumber + " vencida desde el " + Fmt.shortDate(date);
        };
    }
}
