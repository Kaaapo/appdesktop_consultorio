package co.sv.consultorio.domain;

/** Tipo de proceso / tratamiento. Define cuántos meses después de la 1.ª cita toca la 2.ª y el mensaje de WhatsApp. */
public record Process(long id, String name, int monthsToSecond, String messageTemplate) {

    public String monthsLabel() {
        return monthsToSecond == 1 ? "1 mes" : monthsToSecond + " meses";
    }
}
