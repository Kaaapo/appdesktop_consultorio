package co.sv.consultorio.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Datos del formulario de paciente tal como los escribe la usuaria (aún sin validar). */
public record PatientInput(
        String fullName,
        String document,
        String phone,
        Long processId,
        LocalDate birthDate,
        String countryCode,
        List<Long> processIds) {

    /** Sin indicativo: se asume Colombia (57). */
    public PatientInput(String fullName, String document, String phone, Long processId, LocalDate birthDate) {
        this(fullName, document, phone, processId, birthDate, "57");
    }

    public PatientInput(String fullName, String document, String phone, Long processId, LocalDate birthDate,
                        String countryCode) {
        this(fullName, document, phone, processId, birthDate, countryCode,
                processId == null ? List.of() : List.of(processId));
    }

    /** Varios procedimientos. El primero queda como principal. */
    public PatientInput(String fullName, String document, String phone, List<Long> processIds, LocalDate birthDate,
                        String countryCode) {
        this(fullName, document, phone, first(processIds), birthDate, countryCode, copy(processIds));
    }

    /** Ids marcados, sin repetir. Si no hay lista, usa el procedimiento principal. */
    public List<Long> procedureIds() {
        if (processIds != null && !processIds.isEmpty()) {
            return copy(processIds);
        }
        return processId == null ? List.of() : List.of(processId);
    }

    private static Long first(List<Long> ids) {
        List<Long> copy = copy(ids);
        return copy.isEmpty() ? null : copy.get(0);
    }

    private static List<Long> copy(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }
}
