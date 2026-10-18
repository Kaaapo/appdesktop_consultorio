package co.sv.consultorio.app;

import co.sv.consultorio.service.Services;
import javafx.application.HostServices;
import javafx.stage.Stage;

import java.net.URI;
import java.nio.file.Path;

/** Lo que comparten todas las pantallas. */
public final class AppContext {

    private final Services services;
    private final Stage stage;
    private final HostServices host;
    private final Path dataDir;
    private Shell shell;

    AppContext(Services services, Stage stage, HostServices host, Path dataDir) {
        this.services = services;
        this.stage = stage;
        this.host = host;
        this.dataDir = dataDir;
    }

    public Services s() {
        return services;
    }

    public Stage stage() {
        return stage;
    }

    public Shell shell() {
        return shell;
    }

    void setShell(Shell shell) {
        this.shell = shell;
    }

    public Path dataDir() {
        return dataDir;
    }

    public void open(URI uri) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Exception ignored) {
            // Si el escritorio no puede abrirlo, se usa el mecanismo de JavaFX.
        }
        host.showDocument(uri.toASCIIString());
    }

    public void open(Path file) {
        host.showDocument(file.toUri().toString());
    }
}
