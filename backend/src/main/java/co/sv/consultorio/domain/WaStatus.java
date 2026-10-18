package co.sv.consultorio.domain;

/** Estado del WhatsApp de una cita. */
public enum WaStatus {
    /** Todavía no se le ha escrito. */
    NONE,
    /** Se le envió el mensaje y aún no hay respuesta. */
    AVISADO,
    /** La persona contestó por WhatsApp. */
    AGENDADO,
    /** No contestó. Se le puede escribir de nuevo. */
    NO_RESPONDIO;

    public boolean canSend() {
        return this == NONE || this == NO_RESPONDIO;
    }

    /** Mensaje enviado y todavía sin clasificar la respuesta. */
    public boolean awaitingReply() {
        return this == AVISADO;
    }

    public String label() {
        return switch (this) {
            case NONE -> "Sin avisar";
            case AVISADO -> "Avisado";
            case AGENDADO -> "Agendado";
            case NO_RESPONDIO -> "No respondió";
        };
    }
}
