package co.sv.consultorio.service;

import java.time.LocalDate;

/** Datos del formulario de paciente tal como los escribe la usuaria (aún sin validar). */
public record PatientInput(
        String fullName,
        String document,
        String phone,
        Long processId,
        LocalDate birthDate,
        String countryCode) {

    /** Sin indicativo: se asume Colombia (57). */
    public PatientInput(String fullName, String document, String phone, Long processId, LocalDate birthDate) {
        this(fullName, document, phone, processId, birthDate, "57");
    }
}
