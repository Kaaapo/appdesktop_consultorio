package co.sv.consultorio.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

public record Patient(
        long id,
        String fullName,
        String document,
        String phone,
        long processId,
        LocalDate birthDate,
        LocalDateTime registeredAt,
        boolean active) {

    public Patient withActive(boolean value) {
        return new Patient(id, fullName, document, phone, processId, birthDate, registeredAt, value);
    }

    /** Años cumplidos a {@code today}, o null si no hay fecha de nacimiento. */
    public Integer age(LocalDate today) {
        if (birthDate == null || today == null || birthDate.isAfter(today)) {
            return null;
        }
        return Period.between(birthDate, today).getYears();
    }
}
