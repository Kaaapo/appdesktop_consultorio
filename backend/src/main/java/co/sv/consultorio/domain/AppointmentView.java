package co.sv.consultorio.domain;

/** Cita con su paciente, procedimiento y número de cita (0 si es un control). */
public record AppointmentView(Appointment appointment, Patient patient, Process process, int citaNumber) {

    public String processAndType() {
        return (process == null ? "" : process.name() + " · ") + appointment.type().numbered(citaNumber).toLowerCase();
    }

    public String typeAndProcess() {
        return appointment.type().numbered(citaNumber) + (process == null ? "" : " · " + process.name());
    }
}
