package co.sv.consultorio.domain;

import java.time.LocalDateTime;

/**
 * Cita de un paciente. El aviso de WhatsApp pertenece a la cita.
 * Reprogramar la cita borra el aviso y permite escribir de nuevo.
 */
public record Appointment(
        long id,
        long patientId,
        LocalDateTime startsAt,
        AppointmentType type,
        LocalDateTime notifiedAt,
        String notifiedText,
        WaStatus waStatus,
        LocalDateTime createdAt,
        Long processId) {

    public Appointment {
        if (waStatus == null) {
            waStatus = WaStatus.NONE;
        }
    }

    /** Ya se envió un mensaje y no se puede mandar otro hasta registrar que no respondió, o reprogramar. */
    public boolean blocksAnotherMessage() {
        return !waStatus.canSend();
    }
}
