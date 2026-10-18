package co.sv.consultorio.service;

/** Reemplaza las variables {nombre} {tipo} {procedimiento} {fecha} del mensaje de WhatsApp. {proceso} sigue valiendo. */
public final class MessageTemplates {

    public static final String NOMBRE = "{nombre}";
    public static final String TIPO = "{tipo}";
    public static final String PROCESO = "{proceso}";
    public static final String PROCEDIMIENTO = "{procedimiento}";
    public static final String FECHA = "{fecha}";

    public static final String DEFAULT_TEMPLATE =
            "Hola {nombre}, te recordamos tu {tipo} de {procedimiento} el {fecha}, ¿te queda bien esa fecha y hora?";

    private MessageTemplates() {
    }

    public static String render(String template, String nombre, String proceso, String fecha) {
        return render(template, nombre, proceso, fecha, "cita");
    }

    /**
     * {@code tipo} es {@code cita} o {@code control}. Si la plantilla no usa {tipo} y es un control,
     * las frases que dicen «cita» pasan a decir «control».
     */
    public static String render(String template, String nombre, String proceso, String fecha, String tipo) {
        if (template == null) {
            return "";
        }
        String word = tipo == null || tipo.isBlank() ? "cita" : tipo.trim().toLowerCase(java.util.Locale.ROOT);
        boolean explicit = template.contains(TIPO);
        String text = template
                .replace(NOMBRE, nombre == null ? "" : nombre)
                .replace(TIPO, word)
                .replace(PROCEDIMIENTO, proceso == null ? "" : proceso)
                .replace(PROCESO, proceso == null ? "" : proceso)
                .replace(FECHA, fecha == null ? "" : fecha);
        if (!explicit && "control".equals(word)) {
            text = citaToControl(text);
        }
        return text;
    }

    private static String citaToControl(String text) {
        String t = text
                .replace("segunda cita", "control")
                .replace("Segunda cita", "Control")
                .replace("tu cita", "tu control")
                .replace("Tu cita", "Tu control")
                .replace("la cita", "el control")
                .replace("La cita", "El control")
                .replace("una cita", "un control")
                .replace("Una cita", "Un control");
        t = t.replaceAll("\\bCita\\b", "Control");
        t = t.replaceAll("\\bcita\\b", "control");
        return t;
    }
}
