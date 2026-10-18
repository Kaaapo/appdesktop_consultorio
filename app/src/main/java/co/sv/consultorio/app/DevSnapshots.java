package co.sv.consultorio.app;

import co.sv.consultorio.app.dialogs.DailyPopup;
import co.sv.consultorio.app.dialogs.WhatsAppFlow;
import co.sv.consultorio.domain.AppSettings;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.domain.PatientOverview;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Solo para desarrollo: recorre las pantallas y guarda capturas PNG
 * para compararlas con los mockups. Se activa con {@code --snapshots=<carpeta>}.
 */
final class DevSnapshots {

    private final AppContext ctx;
    private final Path dir;
    private final Deque<Runnable> steps = new ArrayDeque<>();

    private DevSnapshots(AppContext ctx, Path dir) {
        this.ctx = ctx;
        this.dir = dir;
    }

    static void run(AppContext ctx, Path dir) {
        new DevSnapshots(ctx, dir).start();
    }

    private void start() {
        Shell sh = ctx.shell();
        setTheme(AppSettings.ThemeMode.LIGHT, AppSettings.FontSize.NORMAL);
        shot("01_login");
        step(() -> {
            AppSettings st = ctx.s().settings().get();
            ctx.s().settings().save(st.withLastPopupDate(null));
            sh.loggedIn();
        });
        shot("02_inicio_popup");
        step(() -> sh.overlay().closeAll());
        shot("03_inicio");
        step(() -> sh.go(Shell.Nav.PACIENTES));
        shot("04_pacientes");
        step(() -> {
            Node more = sh.scene().getRoot().lookupAll(".gh").stream()
                    .filter(n -> n instanceof Button b && "Más acciones".equals(b.getAccessibleText()))
                    .findFirst().orElse(null);
            if (more instanceof Button b) {
                b.fire();
            }
        });
        shot("05_pacientes_menu");
        step(() -> {
            sh.overlay().closeAll();
            sh.goPaciente(null);
        });
        shot("06_paciente");
        step(() -> sh.go(Shell.Nav.PROCESOS));
        shot("07_procesos");
        shot("08_sin_recordatorios");
        step(() -> {
            PatientOverview o = ctx.s().patients().overviews().stream()
                    .filter(p -> p.patient().active() && !p.hasAppointment()).findFirst().orElse(null);
            sh.goCitas(o == null ? null : o.patient().id());
        });
        shot("09_citas");
        step(() -> sh.go(Shell.Nav.REPORTES));
        shot("10_reportes");
        step(() -> sh.go(Shell.Nav.PERFIL));
        shot("11_perfil");
        step(() -> {
            sh.go(Shell.Nav.INICIO);
            AppointmentView v = ctx.s().dashboard().pending().stream()
                    .filter(p -> p.hasAppointment()).map(p -> p.appointment()).findFirst().orElse(null);
            if (v != null) {
                WhatsAppFlow.message(ctx, v, null);
            }
        });
        shot("12_mensaje");
        step(() -> {
            sh.overlay().closeAll();
            AppointmentView v = ctx.s().dashboard().awaitingReply().stream().findFirst().orElse(null);
            if (v != null) {
                WhatsAppFlow.alreadyNotified(ctx, v);
            }
        });
        shot("13_avisado");
        step(() -> {
            sh.overlay().closeAll();
            setTheme(AppSettings.ThemeMode.DARK, AppSettings.FontSize.NORMAL);
            sh.go(Shell.Nav.INICIO);
        });
        shot("14_inicio_oscuro");
        step(() -> sh.go(Shell.Nav.CITAS));
        shot("15_citas_oscuro");
        step(() -> sh.go(Shell.Nav.REPORTES));
        shot("16_reportes_oscuro");
        step(() -> {
            setTheme(AppSettings.ThemeMode.LIGHT, AppSettings.FontSize.LARGE);
            sh.go(Shell.Nav.PACIENTES);
        });
        shot("17_pacientes_grande");
        step(() -> {
            setTheme(AppSettings.ThemeMode.LIGHT, AppSettings.FontSize.NORMAL);
            DailyPopup.show(ctx);
        });
        shot("18_popup");
        step(() -> {
            sh.overlay().closeAll();
            sh.goCitas(null);
        });
        step(() -> press(".pr2"));
        step(() -> fireText("Solo asignar la cita", true));
        step(() -> {
            sh.scene().getRoot().lookupAll(".slot").stream()
                    .filter(n -> n instanceof Button b && !b.getStyleClass().contains("bz") && !b.isDisabled())
                    .map(n -> (Button) n).reduce((a, b) -> b).ifPresent(Button::fire);
        });
        shot("19_citas_lista_para_asignar");
        step(() -> fireText("Solo asignar la cita", false));
        shot("20_citas_asignada");
        step(() -> fireText("Deshacer", false));
        shot("21_citas_deshecha");
        step(() -> fireText("Lista", false));
        shot("22_citas_lista");
        step(() -> {
            try {
                Path tpl = Files.createTempFile("plantilla", ".xlsx");
                ctx.s().imports().writeTemplate(tpl);
                co.sv.consultorio.app.dialogs.ImportReviewDialog.show(ctx, ctx.s().imports().parse(tpl), null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        shot("23_importar");
        step(Platform::exit);
        next();
    }

    private void setTheme(AppSettings.ThemeMode mode, AppSettings.FontSize size) {
        AppSettings st = ctx.s().settings().get();
        ctx.s().settings().save(st.withTheme(mode).withFontSize(size));
        ctx.shell().rebuild();
    }

    private void press(String selector) {
        Node n = ctx.shell().scene().getRoot().lookup(selector);
        if (n != null && n.getOnKeyPressed() != null) {
            n.getOnKeyPressed().handle(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.ENTER, false, false, false, false));
        } else {
            System.err.println("No encontré " + selector);
        }
    }

    private void fireText(String text, boolean dryRun) {
        Node n = java.util.stream.Stream.concat(ctx.shell().scene().getRoot().lookupAll(".button").stream(),
                        ctx.shell().scene().getRoot().lookupAll(".seg-btn").stream())
                .filter(b -> b instanceof javafx.scene.control.ButtonBase bb && text.equals(bb.getText()))
                .findFirst().orElse(null);
        if (n == null) {
            System.err.println("No encontré el botón " + text);
        } else if (!dryRun) {
            ((javafx.scene.control.ButtonBase) n).fire();
        }
    }

    private void step(Runnable r) {
        steps.add(r);
    }

    private void shot(String name) {
        steps.add(() -> {
            try {
                Files.createDirectories(dir);
                WritableImage img = ctx.shell().scene().getRoot().snapshot(null, null);
                try (OutputStream out = Files.newOutputStream(dir.resolve(name + ".png"))) {
                    writePng(img, out);
                }
            } catch (IOException e) {
                System.err.println("No se pudo guardar " + name + ": " + e);
            }
        });
    }

    private void next() {
        Runnable r = steps.poll();
        if (r == null) {
            return;
        }
        PauseTransition p = new PauseTransition(Duration.millis(700));
        p.setOnFinished(e -> {
            try {
                r.run();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
            next();
        });
        p.play();
    }

    private static void writePng(WritableImage img, OutputStream out) throws IOException {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        PixelReader pr = img.getPixelReader();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(raw)) {
            byte[] line = new byte[w * 3 + 1];
            for (int y = 0; y < h; y++) {
                line[0] = 0;
                for (int x = 0; x < w; x++) {
                    int argb = pr.getArgb(x, y);
                    line[1 + x * 3] = (byte) (argb >> 16);
                    line[2 + x * 3] = (byte) (argb >> 8);
                    line[3 + x * 3] = (byte) argb;
                }
                z.write(line);
            }
        }
        DataOutputStream d = new DataOutputStream(out);
        d.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream hd = new DataOutputStream(ihdr);
        hd.writeInt(w);
        hd.writeInt(h);
        hd.writeByte(8);
        hd.writeByte(2);
        hd.writeByte(0);
        hd.writeByte(0);
        hd.writeByte(0);
        chunk(d, "IHDR", ihdr.toByteArray());
        chunk(d, "IDAT", raw.toByteArray());
        chunk(d, "IEND", new byte[0]);
        d.flush();
    }

    private static void chunk(DataOutputStream d, String type, byte[] data) throws IOException {
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        d.writeInt(data.length);
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }
}
