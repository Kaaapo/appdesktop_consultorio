package co.sv.consultorio.app;

import co.sv.consultorio.app.ui.Images;
import co.sv.consultorio.db.Database;
import co.sv.consultorio.service.Services;
import co.sv.consultorio.service.TimeSource;
import co.sv.consultorio.util.Fmt;
import javafx.application.Application;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConsultorioApp extends Application {

    private static final String[] FONTS = {"Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf",
            "PlayfairDisplay-Medium.ttf", "PlayfairDisplay-SemiBold.ttf"};

    private Database db;

    @Override
    public void start(Stage stage) throws Exception {
        Locale.setDefault(Fmt.LOCALE);
        loadFonts();

        Path dir = dataDir();
        Files.createDirectories(dir);
        db = Database.openFile(dir.resolve("consultorio.db"));
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
        stage.setMinWidth(1280);
        stage.setMinHeight(720);
        stage.setWidth(1280);
        stage.setHeight(900);
        stage.setScene(shell.scene());
        shell.showLogin();
        String snaps = getParameters().getRaw().stream()
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
