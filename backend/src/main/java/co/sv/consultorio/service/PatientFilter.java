package co.sv.consultorio.service;

import java.time.LocalDate;

/**
 * Filtros de la lista de pacientes y de los reportes.
 *
 * @param inactiveOnly true = ver solo inactivos (lista de pacientes)
 * @param includeInactive true = activos e inactivos (reportes)
 */
public record PatientFilter(
        String query,
        Long processId,
        CitaFilter cita,
        WhatsAppFilter whatsApp,
        LocalDate registeredFrom,
        LocalDate registeredTo,
        boolean inactiveOnly,
        boolean includeInactive) {

    public enum CitaFilter { ALL, WITH, WITHOUT }

    public enum WhatsAppFilter { ALL, SIN_AVISAR, AVISADO, AGENDADO, NO_RESPONDIO, SIN_RESPUESTA }

    public static PatientFilter all() {
        return new PatientFilter(null, null, CitaFilter.ALL, WhatsAppFilter.ALL, null, null, false, false);
    }
}
