package co.sv.consultorio.app;

import co.sv.consultorio.app.ui.Images;
import co.sv.consultorio.db.Database;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.service.Services;
import co.sv.consultorio.service.TimeSource;
import co.sv.consultorio.util.Fmt;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConsultorioApp extends Application {

    private static final String[] FONTS = {"Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf",
            "PlayfairDisplay-Medium.ttf", "PlayfairDisplay-SemiBold.ttf"};

    private Database db;
    private FileChannel lockChannel;
    private FileLock lock;

    @Override
    public void start(Stage stage) throws Exception {
        Locale.setDefault(Fmt.LOCALE);
        loadFonts();

        Path dir = dataDir();
        Files.createDirectories(dir);
        if (!lockDataDir(dir)) {
            return;
        }
        try {
            db = Database.openFile(dir.resolve("consultorio.db"));
        } catch (RuntimeException e) {
            rememberError(e);
            tell("No se pudo abrir la base de datos",
                    "No borres la carpeta ConsultorioSV. Cierra el programa y avisa a quien lo instaló.");
            Platform.exit();
            return;
        }
        TimeSource time = TimeSource.system();
        try {
            db.dailyBackup(time.today(), 14);
        } catch (RuntimeException e) {
            System.err.println("No se pudo hacer la copia de seguridad: " + e.getMessage());
        }
        Services services = new Services(db, time);

        AppContext ctx = new AppContext(services, stage, getHostServices(), dir);
        Shell shell = new Shell(ctx);
        ctx.setShell(shell);

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("Stephany Valencia · Control de pacientes");
        stage.getIcons().addAll(icons());
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setMinWidth(Math.min(1024, screen.getWidth()));
        stage.setMinHeight(Math.min(600, screen.getHeight()));
        stage.setWidth(Math.min(1280, screen.getWidth()));
        stage.setHeight(Math.min(900, screen.getHeight()));
        installErrorToast(shell);
        stage.setScene(shell.scene());
        shell.showLogin();
        forgetOldWhatsAppNotes();
        boolean packaged = System.getProperty("jpackage.app-version") != null;
        String snaps = packaged ? null : getParameters().getRaw().stream()
                .filter(a -> a.startsWith("--snapshots=")).map(a -> a.substring(12)).findFirst().orElse(null);
        if (snaps == null) {
            shell.chrome().fitToScreen();
        }
        stage.show();
        if (snaps != null) {
            DevSnapshots.run(ctx, Path.of(snaps));
        }
    }

    @Override
    public void stop() {
        if (db != null) {
            db.close();
        }
    }

    /** Un error que nadie atrapó no puede quedar en silencio: se avisa y se deja en la consola. */
    private static void installErrorToast(Shell shell) {
        long[] last = {0};
        Thread.currentThread().setUncaughtExceptionHandler((thread, e) -> {
            e.printStackTrace();
            rememberError(e);
            long now = System.currentTimeMillis();
            if (now - last[0] < 2000) {
                return;
            }
            last[0] = now;
            try {
                shell.error(e instanceof BusinessException && e.getMessage() != null ? e.getMessage()
                        : "No se pudo completar la acción. Inténtalo de nuevo; si sigue pasando, cierra y abre el programa.");
            } catch (RuntimeException ignored) {
                // El aviso también falló: ya quedó en la consola.
            }
        });
    }

    /** Una sola ventana. Si se abre otra, no escribe la misma base al mismo tiempo. */
    private boolean lockDataDir(Path dir) throws java.io.IOException {
        lockChannel = FileChannel.open(dir.resolve("consultorio.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        lock = lockChannel.tryLock();
        if (lock != null) {
            return true;
        }
        tell("El programa ya está abierto", "Búscalo en la barra de tareas.");
        Platform.exit();
        return false;
    }

    private static void tell(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Consultorio SV");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Deja el tipo de error, sin el texto, para no guardar nombres de pacientes. */
    private static void rememberError(Throwable e) {
        try {
            Path dir = dataDir().resolve("logs");
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            sb.append(java.time.LocalDateTime.now()).append(' ').append(e.getClass().getName()).append('\n');
            for (StackTraceElement frame : e.getStackTrace()) {
                sb.append("  at ").append(frame).append('\n');
            }
            Files.writeString(dir.resolve("errores.log"), sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
            // Si no se puede anotar, el aviso en pantalla igual se mostró.
        }
    }

    /** Mensajes de pacientes que quedaron en la carpeta temporal de Windows. */
    private static void forgetOldWhatsAppNotes() {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (java.util.stream.Stream<Path> dirs = Files.list(tmp)) {
            dirs.filter(p -> p.getFileName().toString().startsWith("sv-wa")).forEach(dir -> {
                try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (java.io.IOException ignored) {
                            // Otro envío puede tener el archivo abierto.
                        }
                    });
                } catch (java.io.IOException ignored) {
                    // Se intenta de nuevo la próxima vez.
                }
            });
        } catch (java.io.IOException ignored) {
            // La carpeta temporal no se pudo leer.
        }
    }

    static Path dataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "ConsultorioSV");
        }
        return Path.of(System.getProperty("user.home"), ".consultorio-sv");
    }

    private static void loadFonts() {
        for (String f : FONTS) {
            try (InputStream in = ConsultorioApp.class.getResourceAsStream("/co/sv/consultorio/app/fonts/" + f)) {
                if (in != null) {
                    Font.loadFont(in, 16);
                }
            } catch (Exception e) {
                System.err.println("No se pudo cargar la fuente " + f);
            }
        }
    }

    /** Ícono de la ventana: monograma blanco sobre negro. */
    private static List<Image> icons() {
        List<Image> out = new ArrayList<>();
        Image mono = Images.get("mono.png", true);
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            Rectangle bg = new Rectangle(size, size, Color.web("#0B0B0C"));
            bg.setArcWidth(size * 0.36);
            bg.setArcHeight(size * 0.36);
            ImageView iv = new ImageView(mono);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setFitHeight(size * 0.74);
            StackPane p = new StackPane(bg, iv);
            p.setStyle("-fx-background-color: transparent;");
            new javafx.scene.Scene(p, size, size, Color.TRANSPARENT);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            out.add(p.snapshot(sp, null));
        }
        return out;
    }
}
