package co.sv.consultorio.domain;

/** El administrador elige cita o control. El número de cita lo cuenta el sistema. */
public enum AppointmentType {
    CITA("Cita"),
    CONTROL("Control");

    private final String label;

    AppointmentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String inlineLabel() {
        return this == CONTROL ? "control" : "cita";
    }

    /** "Cita 2" o "Control". {@code number} solo aplica a las citas. */
    public String numbered(int number) {
        return this == CONTROL ? "Control" : "Cita " + Math.max(number, 1);
    }
}
