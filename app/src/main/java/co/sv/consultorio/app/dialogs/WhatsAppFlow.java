package co.sv.consultorio.app.dialogs;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.ui.Chip;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.Images;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.WaStatus;
import co.sv.consultorio.util.Fmt;
import co.sv.consultorio.util.Texts;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Enviar WhatsApp: abre el diálogo de mensaje, o el de "Ya se le avisó" si esa cita ya fue avisada.
 * Sin cita no hay aviso posible: se lleva a la usuaria a asignar la cita.
 */
public final class WhatsAppFlow {

    private WhatsAppFlow() {
    }

    public static void forPatient(AppContext ctx, PatientOverview o) {
        if (o.appointment() == null) {
            ctx.shell().goCitas(o.patient().id());
            ctx.shell().toast("Primero asígnale una cita a " + o.patient().fullName() + "; luego podrás avisarle.");
            return;
        }
        forAppointment(ctx, o.appointment().id());
    }

    public static void forAppointment(AppContext ctx, long appointmentId) {
        AppointmentView v = ctx.s().appointments().view(appointmentId);
        if (v == null) {
            ctx.shell().error("La cita ya no existe.");
            ctx.shell().changed();
            return;
        }
        switch (v.appointment().waStatus()) {
            case AVISADO -> awaitingReply(ctx, v);
            case AGENDADO -> alreadyScheduled(ctx, v);
            default -> message(ctx, v, null);
        }
    }

    private static String subtitle(AppointmentView v) {
        String kind = v.appointment().type().inlineLabel();
        return v.patient().fullName() + " · " + Texts.formatPhone(v.patient().phone()) + " · " + kind + " el "
                + Fmt.appointmentLong(v.appointment().startsAt());
    }

    /** Diálogo "Enviar por WhatsApp". {@code afterSent} se ejecuta cuando se marca como avisado. */
    public static void message(AppContext ctx, AppointmentView v, Runnable afterSent) {
        Pop p = new Pop(ctx, null, "Enviar por WhatsApp", subtitle(v), 30);
        HBox state = Chip.of("na", "Sin avisar por WhatsApp", false);
        p.head().getChildren().add(state);

        String original = ctx.s().notifications().defaultMessage(v);
        TextArea area = Ui.messageArea(true);
        area.setText(original);
        Label lbl = Ui.label("Mensaje", "lbl");
        VBox field = new VBox(Ui.px(6), lbl, area);
        VBox.setVgrow(area, Priority.ALWAYS);
        HBox body = new HBox(Ui.px(16), Ui.hgrow(field), locationPhoto());

        String procName = v.process() == null ? "programa" : v.process().name();
        Button reset = Ui.link("Restablecer mensaje");
        reset.getStyleClass().addAll("plain", "sm");
        reset.setStyle("-fx-font-size: " + Ui.px(14) + "px; -fx-padding: " + Ui.px(8) + "px " + Ui.px(12) + "px;");
        reset.setOnAction(e -> area.setText(original));
        HBox tools = new HBox(Ui.px(12), Chip.of("comp", "Mensaje del " + procName, false), Ui.hspacer(), reset);
        tools.setAlignment(Pos.CENTER_LEFT);

        Button cancel = Ui.btn2("Cancelar");
        cancel.setOnAction(e -> p.close());
        Button open = Ui.btn("Abrir WhatsApp", Icons.CHAT);
        open.setOnAction(e -> {
            String text = area.getText() == null ? "" : area.getText().trim();
            if (text.isEmpty()) {
                ctx.shell().error("Escribe el mensaje antes de abrir WhatsApp.");
                return;
            }
            open.setDisable(true);
            openDesktopChat(ctx, ctx.s().notifications().whatsAppDesktopUri(
                    v.patient().countryCode(), v.patient().phone()).toString(), text, ok -> {
                if (ok) {
                    confirmSent(ctx, p, v, text, afterSent);
                } else {
                    open.setDisable(false);
                    var content = new javafx.scene.input.ClipboardContent();
                    content.putString(text);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                    ctx.shell().error("No se pudo pegar el mensaje en WhatsApp. Quedó copiado: abre el chat y pulsa Ctrl+V. La foto hay que adjuntarla a mano.");
                }
            });
        });
        p.add(body, tools, Pop.foot(
                "Se abre WhatsApp en el chat de este número. El mensaje y la foto se pegan ahí. "
                        + "No se abre el navegador.",
                cancel, open));
        p.show(660);
    }

    /** Segundo paso: confirmar que el mensaje se envió para marcar la cita como avisada. */
    private static void confirmSent(AppContext ctx, Pop p, AppointmentView v, String text, Runnable afterSent) {
        p.box.getChildren().subList(1, p.box.getChildren().size()).clear();
        Label info = Ui.wrap("WhatsApp quedó abierto para " + v.patient().fullName()
                + ". Revisa que estén el mensaje y la foto, envíalos y márcalo aquí.",
                "ink2", "fs16");
        TextArea shown = Ui.messageArea(false);
        shown.setText(text);
        VBox preview = new VBox(shown);
        preview.getStyleClass().add("ro-box");
        Button notYet = Ui.btn2("Todavía no lo envié");
        notYet.setOnAction(e -> p.close());
        Button sent = Ui.btn("Sí, ya lo envié", Icons.CHECK);
        sent.getStyleClass().add("autofocus");
        sent.setOnAction(e -> {
            try {
                ctx.s().notifications().markNotified(v.appointment().id(), text);
                p.close();
                ctx.shell().toast("Avisado: el mensaje quedó registrado. Falta saber si respondió.");
                if (afterSent != null) {
                    afterSent.run();
                }
                ctx.shell().changed();
            } catch (BusinessException ex) {
                p.close();
                ctx.shell().error(ex.getMessage());
                ctx.shell().changed();
            }
        });
        p.add(info, preview, Pop.foot(null, notYet, sent));
        javafx.application.Platform.runLater(sent::requestFocus);
    }

    /** El mensaje ya se envió. Aquí se registra si contestó o si no. */
    public static void awaitingReply(AppContext ctx, AppointmentView v) {
        Pop p = new Pop(ctx, null, "¿Respondió por WhatsApp?", subtitle(v), 30);
        var a = v.appointment();
        Label strong = Ui.wrap("Aviso enviado el " + Fmt.longDate(a.notifiedAt().toLocalDate()) + ", "
                + Fmt.time12(a.notifiedAt().toLocalTime()), "w600", "fs16");
        Label why = Ui.wrap("Agendado: contestó. No respondió: no contestó y se le puede escribir otra vez.", "fs16");
        VBox txt = new VBox(Ui.px(2), strong, why);
        HBox box = new HBox(Ui.px(14), Icons.of(Icons.CHECK_CIRCLE, 22, 1.7), Ui.hgrow(txt));
        box.getStyleClass().add("avis-box");
        box.setAlignment(Pos.TOP_LEFT);
        Button no = Ui.btn2("No respondió");
        no.setOnAction(e -> finish(ctx, p, v, false));
        Button yes = Ui.btn("Respondió, agendado", Icons.CHECK);
        yes.getStyleClass().add("autofocus");
        yes.setOnAction(e -> finish(ctx, p, v, true));
        p.add(box, Pop.foot(null, no, yes));
        p.show(660);
    }

    private static void finish(AppContext ctx, Pop p, AppointmentView v, boolean scheduled) {
        try {
            if (scheduled) {
                ctx.s().notifications().markScheduled(v.appointment().id());
                ctx.shell().toast(v.patient().fullName() + " quedó agendado.");
            } else {
                ctx.s().notifications().markNoReply(v.appointment().id());
                ctx.shell().toast(v.patient().fullName() + " no respondió. Puedes enviarle otro mensaje.");
            }
            p.close();
            ctx.shell().changed();
        } catch (BusinessException ex) {
            ctx.shell().error(ex.getMessage());
        }
    }

    /** Ya contestó: no se manda otro mensaje por esta cita. */
    public static void alreadyScheduled(AppContext ctx, AppointmentView v) {
        Pop p = new Pop(ctx, null, "Ya quedó agendado", subtitle(v), 30);
        Label strong = Ui.wrap("La persona respondió por WhatsApp.", "w600", "fs16");
        Label why = Ui.wrap("No hace falta enviarle otro mensaje por esta cita.", "fs16");
        VBox txt = new VBox(Ui.px(2), strong, why);
        HBox box = new HBox(Ui.px(14), Icons.of(Icons.CHECK_CIRCLE, 22, 1.7), Ui.hgrow(txt));
        box.getStyleClass().add("avis-box");
        box.setAlignment(Pos.TOP_LEFT);
        Button close = Ui.btn("Listo");
        close.getStyleClass().add("autofocus");
        close.setOnAction(e -> p.close());
        p.add(box, Pop.foot(null, close));
        p.show(660);
    }

    /** Abre el chat y, en la misma ventana de WhatsApp, pega el mensaje y luego la foto. */
    private static void openDesktopChat(AppContext ctx, String link, String text, java.util.function.Consumer<Boolean> then) {
        Image photo = Images.get("ubicacion.jpg", false);
        ctx.stage().toBack();
        Thread worker = new Thread(() -> {
            boolean opened = false;
            Path dir = null;
            try {
                dir = java.nio.file.Files.createTempDirectory("sv-wa");
                Path msg = dir.resolve("mensaje.txt");
                java.nio.file.Files.writeString(msg, text == null ? "" : text, java.nio.charset.StandardCharsets.UTF_8);
                Path img = dir.resolve("ubicacion.png");
                writePng(photo, img);
                Path script = dir.resolve("pegar.ps1");
                java.nio.file.Files.writeString(script, PASTE_SCRIPT, java.nio.charset.StandardCharsets.UTF_8);
                String powershell = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
                        "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
                ProcessBuilder launch = new ProcessBuilder(
                        powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-STA", "-File", script.toString());
                launch.environment().put("WA_URL", link);
                launch.environment().put("WA_MSG", msg.toAbsolutePath().toString());
                launch.environment().put("WA_IMG", img.toAbsolutePath().toString());
                launch.redirectErrorStream(true);
                Process process = launch.start();
                boolean finished = process.waitFor(25, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }
                opened = finished && process.exitValue() == 0;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                opened = false;
            } finally {
                deleteTree(dir);
            }
            boolean ok = opened;
            javafx.application.Platform.runLater(() -> {
                ctx.stage().toFront();
                then.accept(ok);
            });
        }, "abrir-whatsapp");
        worker.setDaemon(true);
        worker.start();
    }

    private static void writePng(Image img, Path file) throws java.io.IOException {
        int w = Math.max(1, (int) img.getWidth());
        int h = Math.max(1, (int) img.getHeight());
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader reader = img.getPixelReader();
        if (reader != null) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out.setRGB(x, y, reader.getArgb(x, y));
                }
            }
        }
        javax.imageio.ImageIO.write(out, "png", file.toFile());
    }

    /** Borra el mensaje temporal del paciente en cuanto termina el pegado. */
    private static void deleteTree(Path dir) {
        if (dir == null) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (java.io.IOException ignored) {
                    // Si Windows todavía tiene el archivo abierto, se limpia en el próximo arranque.
                }
            });
        } catch (java.io.IOException ignored) {
            // Igual que arriba.
        }
    }

    private static final String PASTE_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Windows.Forms
            Add-Type -AssemblyName System.Drawing
            Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class SvClick { [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L; public int T; public int R; public int B; } [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r); [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y); [DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e); [DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo); public static void Bottom(IntPtr h) { RECT r; if (!GetWindowRect(h, out r)) return; SetCursorPos((r.L + r.R) / 2, r.B - 48); mouse_event(2, 0, 0, 0, 0); mouse_event(4, 0, 0, 0, 0); } public static void Paste() { keybd_event(0x11, 0, 0, UIntPtr.Zero); System.Threading.Thread.Sleep(40); keybd_event(0x56, 0, 0, UIntPtr.Zero); System.Threading.Thread.Sleep(40); keybd_event(0x56, 0, 2, UIntPtr.Zero); keybd_event(0x11, 0, 2, UIntPtr.Zero); } }'
            Start-Process -FilePath $env:WA_URL
            $p = $null
            foreach ($i in 1..30) {
              $p = Get-Process -Name 'WhatsApp.Root','WhatsApp' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
              if ($p) { break }
              Start-Sleep -Milliseconds 300
            }
            if (-not $p) { exit 1 }
            $w = New-Object -ComObject WScript.Shell
            function Focus-Wa {
              $title = $p.MainWindowTitle
              if (-not $title) { $title = 'WhatsApp' }
              [void]$w.AppActivate($title)
              Start-Sleep -Milliseconds 200
              [void]$w.AppActivate([int]$p.Id)
              Start-Sleep -Milliseconds 300
              [SvClick]::Bottom($p.MainWindowHandle)
              Start-Sleep -Milliseconds 250
            }
            $text = [System.IO.File]::ReadAllText($env:WA_MSG, (New-Object System.Text.UTF8Encoding $false))
            [System.Windows.Forms.Clipboard]::SetText($text, [System.Windows.Forms.TextDataFormat]::UnicodeText)
            Start-Sleep -Milliseconds 200
            Focus-Wa
            [SvClick]::Paste()
            Start-Sleep -Milliseconds 700
            $img = [System.Drawing.Image]::FromFile($env:WA_IMG)
            [System.Windows.Forms.Clipboard]::Clear()
            [System.Windows.Forms.Clipboard]::SetImage($img)
            Start-Sleep -Milliseconds 250
            [SvClick]::Paste()
            $img.Dispose()
            exit 0
            """;

    private static VBox locationPhoto() {
        ImageView view = new ImageView(Images.get("ubicacion.jpg", false));
        view.setFitWidth(Ui.px(120));
        view.setPreserveRatio(true);
        view.setSmooth(true);
        VBox box = new VBox(Ui.px(8), Ui.label("Foto que se anexa", "lbl"), view);
        box.getStyleClass().add("ro-box");
        box.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return box;
    }

    /** Compatibilidad: abre el diálogo que corresponde al estado actual. */
    public static void alreadyNotified(AppContext ctx, AppointmentView v) {
        if (v.appointment().waStatus() == WaStatus.AGENDADO) {
            alreadyScheduled(ctx, v);
        } else {
            awaitingReply(ctx, v);
        }
    }

}
