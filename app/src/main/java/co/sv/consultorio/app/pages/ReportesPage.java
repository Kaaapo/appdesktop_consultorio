package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.app.dialogs.ImportReviewDialog;
import co.sv.consultorio.app.ui.Choice;
import co.sv.consultorio.app.ui.DateField;
import co.sv.consultorio.app.ui.Field;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.service.PatientFilter;
import co.sv.consultorio.service.ReportService;
import co.sv.consultorio.util.Fmt;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReportesPage extends Page {

    private boolean range = true;
    private LocalDate from;
    private LocalDate to;
    private Long processId;
    private PatientFilter.CitaFilter cita = PatientFilter.CitaFilter.ALL;
    private PatientFilter.WhatsAppFilter wa = PatientFilter.WhatsAppFilter.ALL;
    private boolean includeInactive;

    private final VBox preview = Ui.card();
    private File lastDir;

    public ReportesPage(AppContext ctx) {
        super(ctx);
        LocalDate today = s().time().today();
        to = today;
        from = today.minusMonths(1).withDayOfMonth(1);
    }

    @Override
    public Shell.Nav nav() {
        return Shell.Nav.REPORTES;
    }

    @Override
    public void render() {
        VBox hd = new VBox(Ui.px(8), Ui.h1("Reportes"),
                Ui.lead("Genera listados en Excel o PDF e importa pacientes desde un Excel."));

        Node form = form();
        preview.setSpacing(Ui.px(16));
        preview.setStyle("-fx-padding: " + Ui.px(24) + "px;");
        preview.setPrefWidth(Ui.px(380));
        HBox.setHgrow(preview, Priority.ALWAYS);
        HBox top = new HBox(Ui.px(20), form, preview);
        top.setAlignment(Pos.TOP_LEFT);
        top.setFillHeight(false);

        main.getChildren().setAll(hd, top, importCard());
        updatePreview();
    }

    @Override
    public void refresh() {
        updatePreview();
    }

    private ReportService.ReportRequest request() {
        return new ReportService.ReportRequest(range, from, to, processId, cita, wa, includeInactive);
    }

    private Node form() {
        VBox card = Ui.card();
        card.setSpacing(Ui.px(16));
        card.setStyle("-fx-padding: " + Ui.px(24) + "px;");
        card.setPrefWidth(Ui.px(380));
        HBox.setHgrow(card, Priority.ALWAYS);

        ToggleGroup tg = new ToggleGroup();
        RadioButton total = new RadioButton();
        RadioButton byRange = new RadioButton();
        total.setToggleGroup(tg);
        byRange.setToggleGroup(tg);
        (range ? byRange : total).setSelected(true);

        DateField fromField = new DateField(from);
        DateField toField = new DateField(to);
        GridPane dates = twoCols(new Field("Desde", fromField), new Field("Hasta", toField));
        dates.setDisable(!range);
        tg.selectedToggleProperty().addListener((o, a, b) -> {
            range = b == byRange;
            dates.setDisable(!range);
            updatePreview();
        });
        fromField.valueProperty().addListener((o, a, b) -> {
            from = b;
            updatePreview();
        });
        toField.valueProperty().addListener((o, a, b) -> {
            to = b;
            updatePreview();
        });

        List<Choice.Opt<Long>> procOpts = new ArrayList<>();
        procOpts.add(Choice.opt("Todos los procedimientos", null));
        for (Process p : s().processes().list()) {
            procOpts.add(Choice.opt(p.name(), p.id()));
        }
        Choice<Long> proc = new Choice<>(procOpts, processId, v -> {
            processId = v;
            updatePreview();
        });
        Choice<PatientFilter.CitaFilter> citaBox = new Choice<>(List.of(
                Choice.opt("Todos", PatientFilter.CitaFilter.ALL),
                Choice.opt("Con cita", PatientFilter.CitaFilter.WITH),
                Choice.opt("Sin cita", PatientFilter.CitaFilter.WITHOUT)), cita, v -> {
            cita = v;
            updatePreview();
        });
        Choice<PatientFilter.WhatsAppFilter> waBox = new Choice<>(List.of(
                Choice.opt("Todos", PatientFilter.WhatsAppFilter.ALL),
                Choice.opt("Sin avisar", PatientFilter.WhatsAppFilter.SIN_AVISAR),
                Choice.opt("Avisados", PatientFilter.WhatsAppFilter.AVISADO),
                Choice.opt("Agendados", PatientFilter.WhatsAppFilter.AGENDADO),
                Choice.opt("No respondieron", PatientFilter.WhatsAppFilter.NO_RESPONDIO)), wa, v -> {
            wa = v;
            updatePreview();
        });
        CheckBox inactive = new CheckBox("Incluir pacientes inactivos");
        inactive.setSelected(includeInactive);
        inactive.selectedProperty().addListener((o, a, b) -> {
            includeInactive = b;
            updatePreview();
        });

        Button excel = Ui.btn("Generar Excel", Icons.FILE_X);
        excel.setOnAction(e -> export(true));
        Button pdf = Ui.btn2("Generar PDF", Icons.FILE);
        pdf.setOnAction(e -> export(false));
        HBox buttons = new HBox(Ui.px(10), excel, pdf);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.getStyleClass().add("sep-line");
        buttons.setStyle("-fx-padding: " + Ui.px(16) + "px 0 0 0;");

        card.getChildren().addAll(Ui.h2("Generar reporte"),
                opt(total, "Total de pacientes", "Todos los pacientes activos hoy."),
                opt(byRange, "Pacientes registrados en un rango", "Los que se agregaron entre dos fechas."),
                dates, new Field("Procedimiento", proc),
                twoCols(new Field("Cita", citaBox), new Field("WhatsApp", waBox)),
                inactive, buttons);
        return card;
    }

    private static GridPane twoCols(Node a, Node b) {
        GridPane g = new GridPane();
        g.setHgap(Ui.px(12));
        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(50);
        g.getColumnConstraints().addAll(c, c);
        g.add(a, 0, 0);
        g.add(b, 1, 0);
        return g;
    }

    private Node opt(RadioButton rb, String title, String sub) {
        rb.setAccessibleText(title);
        VBox text = new VBox(Ui.px(2), Ui.label(title, "w600"), Ui.wrap(sub, "muted", "fs14"));
        HBox box = new HBox(Ui.px(14), rb, Ui.hgrow(text));
        box.getStyleClass().add("opt-btn");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setCursor(javafx.scene.Cursor.HAND);
        box.setOnMouseClicked(e -> {
            rb.setSelected(true);
            rb.requestFocus();
        });
        return box;
    }

    private void updatePreview() {
        ReportService.ReportRequest r = request();
        List<PatientOverview> rows = s().reports().rows(r);
        Map<String, Integer> counts = s().reports().countsByProcess(r, rows);
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        Label title = Ui.h2(Fmt.count(rows.size(), range ? "paciente registrado" : "paciente",
                range ? "pacientes registrados" : "pacientes"));
        VBox head = new VBox(Ui.px(6), Ui.eyebrow("Vista previa"), title, Ui.label(s().reports().describe(r), "muted", "fs14"));
        VBox bars = new VBox(Ui.px(12));
        counts.forEach((name, n) -> {
            Label l = Ui.oneLine(name, "fs15");
            l.setPrefWidth(Ui.px(150));
            l.setMaxWidth(Ui.px(150));
            HBox.setHgrow(l, Priority.NEVER);
            Region bar = Ui.cls(new Region(), "bar");
            StackPane track = new StackPane(bar);
            track.getStyleClass().add("bar-track");
            track.setMinWidth(Ui.px(48));
            StackPane.setAlignment(bar, Pos.CENTER_LEFT);
            double frac = max == 0 ? 0 : (double) n / max;
            bar.maxWidthProperty().bind(track.widthProperty().multiply(frac));
            bar.setVisible(n > 0);
            HBox.setHgrow(track, Priority.ALWAYS);
            Label num = Ui.label(String.valueOf(n), "fs15");
            num.setMinWidth(Ui.px(28));
            num.setAlignment(Pos.CENTER_RIGHT);
            HBox row = new HBox(Ui.px(12), l, track, num);
            row.setAlignment(Pos.CENTER_LEFT);
            bars.getChildren().add(row);
        });
        if (counts.isEmpty()) {
            bars.getChildren().add(Ui.wrap("Aún no hay procedimientos ni pacientes.", "muted", "fs14"));
        }
        preview.getChildren().setAll(head, bars,
                Ui.wrap("El archivo incluye los datos del paciente, cuántas citas y controles tiene, y la fecha y hora de cada una.", "muted", "fs13"));
    }

    private static byte[] logoBytes() {
        try (InputStream in = ReportesPage.class.getResourceAsStream("/co/sv/consultorio/app/img/logo.png")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private void export(boolean excel) {
        ReportService.ReportRequest r = request();
        if (r.range() && r.from() != null && r.to() != null && r.from().isAfter(r.to())) {
            shell().error("La fecha “Desde” no puede ser posterior a “Hasta”.");
            return;
        }
        List<PatientOverview> rows = s().reports().rows(r);
        FileChooser fc = new FileChooser();
        fc.setTitle(excel ? "Guardar reporte en Excel" : "Guardar reporte en PDF");
        fc.setInitialFileName("pacientes-" + s().time().today() + (excel ? ".xlsx" : ".pdf"));
        fc.getExtensionFilters().add(excel
                ? new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx")
                : new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf"));
        File dir = lastDir != null ? lastDir : defaultDir();
        if (dir != null && dir.isDirectory()) {
            fc.setInitialDirectory(dir);
        }
        File f = fc.showSaveDialog(ctx.stage());
        if (f == null) {
            return;
        }
        lastDir = f.getParentFile();
        Path path = f.toPath();
        try {
            if (excel) {
                s().reports().writeExcel(rows, path);
            } else {
                s().reports().writePdf(rows, path, "Reporte de pacientes", s().reports().describe(r)
                        + " · " + Fmt.count(rows.size(), "paciente", "pacientes"), logoBytes());
            }
            shell().toast("Reporte guardado: " + f.getName());
            ctx.open(path);
        } catch (IOException | RuntimeException ex) {
            shell().error("No se pudo guardar el archivo: " + ex.getMessage());
        }
    }

    private static File defaultDir() {
        File docs = new File(System.getProperty("user.home"), "Documents");
        if (docs.isDirectory()) {
            return docs;
        }
        File esc = new File(System.getProperty("user.home"), "Documentos");
        return esc.isDirectory() ? esc : new File(System.getProperty("user.home"));
    }

    private Node importCard() {
        Label t = Ui.wrap("Carga la lista que ya tienes. Obligatorios: nombre, documento, indicativo, teléfono "
                + "y fecha de nacimiento. El procedimiento puede ir vacío y se asigna después, al editar. "
                + "Antes de guardar verás una revisión.", "muted", "fs15");
        t.setLineSpacing(Ui.px(3));
        Button template = Ui.btn2("Descargar plantilla", Icons.DOWNLOAD);
        template.setOnAction(e -> downloadTemplate());
        VBox left = new VBox(Ui.px(10), Ui.h2("Importar pacientes desde Excel"), t, template);
        left.setPrefWidth(Ui.px(340));
        HBox.setHgrow(left, Priority.ALWAYS);

        Button pick = Ui.btn("Seleccionar archivo");
        pick.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Elegir archivo de Excel");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"));
            File dir = lastDir != null ? lastDir : defaultDir();
            if (dir != null && dir.isDirectory()) {
                fc.setInitialDirectory(dir);
            }
            File f = fc.showOpenDialog(ctx.stage());
            if (f != null) {
                lastDir = f.getParentFile();
                importFile(f);
            }
        });
        VBox drop = new VBox(Ui.px(8), co.sv.consultorio.app.ui.Icons.of(Icons.UPLOAD, 22),
                Ui.label("Arrastra el archivo .xlsx aquí", "w600"), pick);
        drop.getStyleClass().add("drop");
        drop.setPrefWidth(Ui.px(300));
        HBox.setHgrow(drop, Priority.ALWAYS);
        drop.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
                if (!drop.getStyleClass().contains("over")) {
                    drop.getStyleClass().add("over");
                }
            }
            e.consume();
        });
        drop.setOnDragExited(e -> drop.getStyleClass().remove("over"));
        drop.setOnDragDropped(e -> {
            boolean ok = false;
            if (e.getDragboard().hasFiles() && !e.getDragboard().getFiles().isEmpty()) {
                File f = e.getDragboard().getFiles().get(0);
                ok = true;
                javafx.application.Platform.runLater(() -> importFile(f));
            }
            e.setDropCompleted(ok);
            e.consume();
        });

        HBox card = new HBox(Ui.px(22), left, drop);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-padding: " + Ui.px(24) + "px;");
        return card;
    }

    private void importFile(File f) {
        String name = f.getName().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            shell().error("Ese archivo no es un Excel (.xlsx).");
            return;
        }
        try {
            var preview = s().imports().parse(f.toPath());
            if (preview.rows().isEmpty()) {
                shell().error("El archivo no tiene filas con pacientes.");
                return;
            }
            ImportReviewDialog.show(ctx, preview, this::updatePreview);
        } catch (BusinessException ex) {
            shell().error(ex.getMessage());
        } catch (IOException | RuntimeException ex) {
            shell().error("No se pudo leer el archivo. ¿Está abierto en Excel? " + (ex.getMessage() == null ? "" : ex.getMessage()));
        }
    }

    private void downloadTemplate() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar plantilla");
        fc.setInitialFileName("plantilla-pacientes.xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        File dir = lastDir != null ? lastDir : defaultDir();
        if (dir != null && dir.isDirectory()) {
            fc.setInitialDirectory(dir);
        }
        File f = fc.showSaveDialog(ctx.stage());
        if (f == null) {
            return;
        }
        lastDir = f.getParentFile();
        try {
            s().imports().writeTemplate(f.toPath());
            shell().toast("Plantilla guardada: " + f.getName());
            ctx.open(f.toPath());
        } catch (IOException | RuntimeException ex) {
            shell().error("No se pudo guardar la plantilla: " + ex.getMessage());
        }
    }
}
