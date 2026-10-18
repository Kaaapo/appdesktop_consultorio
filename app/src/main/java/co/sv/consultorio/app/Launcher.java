package co.sv.consultorio.app;

import javafx.application.Application;

/**
 * Punto de entrada del .jar / instalador. No extiende {@code Application} para que JavaFX arranque
 * desde el classpath sin módulos.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
        System.setProperty("prism.lcdtext", "false");
        Application.launch(ConsultorioApp.class, args);
    }
}
