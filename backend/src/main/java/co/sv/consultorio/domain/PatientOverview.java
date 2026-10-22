package co.sv.consultorio.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Paciente con su procedimiento, la próxima cita (si existe) y cuántas citas lleva.
 * Los controles no suman al número de cita.
 */
public record PatientOverview(
        Patient patient,
        Process process,
        Appointment appointment,
        int citaCount,
        int controlCount,
        LocalDate lastCitaDate,
        List<Process> allProcesses) {

    /** Procedimientos del paciente. Si la lista viene vacía, usa el principal. */
    public List<Process> procedures() {
        if (allProcesses != null && !allProcesses.isEmpty()) {
            return allProcesses;
        }
        return process == null ? List.of() : List.of(process);
    }

    public String procedureNames() {
        return procedures().stream().map(Process::name).collect(Collectors.joining(", "));
    }

    public boolean hasProcedure(Long id) {
        return id != null && procedures().stream().anyMatch(p -> p.id() == id);
    }

    public boolean hasAppointment() {
        return appointment != null;
    }

    public WaStatus waStatus() {
        return appointment == null || appointment.waStatus() == null ? WaStatus.NONE : appointment.waStatus();
    }

    /** Número que tendría la siguiente cita de seguimiento. */
    public int nextCitaNumber() {
        return citaCount + 1;
    }

    public NextStep nextStep(LocalDate today) {
        if (citaCount == 0 || lastCitaDate == null || process == null) {
            return new NextStep(AppointmentType.CITA, today, NextStep.Kind.NONE);
        }
        LocalDate suggested = lastCitaDate.plusMonths(process.monthsToSecond());
        return suggested.isBefore(today)
                ? new NextStep(AppointmentType.CITA, suggested, NextStep.Kind.OVERDUE)
                : new NextStep(AppointmentType.CITA, suggested, NextStep.Kind.SUGGESTED);
    }
}
