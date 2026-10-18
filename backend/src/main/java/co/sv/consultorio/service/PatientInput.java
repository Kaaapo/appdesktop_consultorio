package co.sv.consultorio.service;

import java.time.LocalDate;

/** Datos del formulario de paciente tal como los escribe la usuaria (aún sin validar). */
public record PatientInput(
        String fullName,
        String document,
        String phone,
        Long processId,
        LocalDate birthDate) {
}
