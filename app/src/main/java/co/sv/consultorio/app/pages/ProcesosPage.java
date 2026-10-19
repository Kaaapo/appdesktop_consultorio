package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.app.ui.Field;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.Table;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.ValidationException;
import co.sv.consultorio.service.MessageTemplates;
import co.sv.consultorio.util.Fmt;
import co.sv.consultorio.util.Texts;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

public final class ProcesosPage extends Page {

    private static final Table TABLE = new Table(180, 0, 80, 100, 44).styled("g10");

    private Process editing;
    private final TextField name = new TextField();
    private final TextField months = new TextField();
    private final TextArea template;
    private final TextArea preview;

    public ProcesosPage(AppContext ctx) {
        super(ctx);
        template = Ui.messageArea(true);
        preview = Ui.messageArea(false);
        months.setTextFormatter(new TextFormatter<String>(c ->
                c.isContentChange() && !c.getControlNewText().matches("\\d{0,2}") ? null : c));
        name.textProperty().addListener((o, a, b) -> updatePreview());
        months.textProperty().addListener((o, a, b) -> updatePreview());
        template.textProperty().addListener((o, a, b) -> updatePreview());
    }

    @Override
    public Shell.Nav nav() {
        return Shell.Nav.PROCESOS;
    }

    @Override
    public void render() {
        VBox hd = new VBox(Ui.h1("Procedimientos"),
                Ui.lead("Cada procedimiento define cada cuántos meses toca la siguiente cita y el mensaje de WhatsApp."));
        hd.setSpacing(Ui.px(8));

        Node list = list();
        Node form = form();
        HBox body = new HBox(Ui.px(22), list, form);
        body.setAlignment(Pos.TOP_LEFT);
        body.setFillHeight(false);
        main.getChildren().setAll(hd, body);
    }

    private Node list() {
        List<Process> procs = s().processes().list();
        Map<Long, Integer> counts = s().processes().activePatientCounts();
        VBox card = Ui.card();
        card.setPrefWidth(Ui.px(560));
        card.setMinWidth(Ui.px(420));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.getChildren().add(TABLE.header("Procedimiento", "Cada", "Pacientes", ""));
        if (procs.isEmpty()) {
            Label empty = Ui.wrap("Aún no hay procedimientos. Crea el primero con el formulario.", "muted", "fs15");
            VBox b = new VBox(empty);
            b.getStyleClass().add("table-foot");
            b.setStyle("-fx-padding: " + Ui.px(24) + "px " + Ui.px(20) + "px;");
            card.getChildren().add(b);
        }
        int total = 0;
        for (Process p : procs) {
            int n = counts.getOrDefault(p.id(), 0);
            total += n;
            Label nm = Ui.wrap(p.name(), "w600");
            Ui.tip(nm, p.name());
            Label msg = Ui.wrap(p.messageTemplate(), "muted", "fs13");
            msg.setLineSpacing(Ui.px(2));
            VBox who = new VBox(Ui.px(4), nm, msg);
            who.setMinWidth(0);
            Button edit = Ui.ghost(Icons.EDIT, "Editar procedimiento", 18);
            edit.setOnAction(e -> startEdit(p));
            GridPane row = TABLE.row(who, Ui.label(p.monthsLabel()),
                    Ui.label(n == 1 ? "1 activo" : n + " activos"), edit);
            row.setAlignment(Pos.TOP_LEFT);
            for (Node c : row.getChildren()) {
                GridPane.setValignment(c, VPos.TOP);
            }
            GridPane.setMargin(edit, new javafx.geometry.Insets(Ui.px(-8), 0, 0, 0));
            row.setStyle("-fx-padding: " + Ui.px(14) + "px " + Ui.px(20) + "px;");
            if (editing != null && editing.id() == p.id()) {
                row.setStyle(row.getStyle() + "-fx-background-color: -c-sel;");
            }
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    startEdit(p);
                }
            });
            card.getChildren().add(row);
        }
        Label foot = Ui.label(Fmt.count(procs.size(), "procedimiento", "procedimientos") + " · "
                + Fmt.count(total, "paciente activo", "pacientes activos"), "muted", "fs14");
        HBox f = new HBox(foot);
        f.getStyleClass().add("table-foot");
        card.getChildren().add(f);
        return card;
    }

    private Node form() {
        VBox card = Ui.card("pbox");
        card.setSpacing(Ui.px(18));
        card.setStyle("-fx-padding: " + Ui.px(26) + "px;");
        card.setPrefWidth(Ui.px(400));
        card.setMinWidth(Ui.px(340));
        HBox.setHgrow(card, Priority.ALWAYS);

        Label h2 = Ui.h2(editing == null ? "Nuevo procedimiento" : "Editar " + editing.name());
        h2.setStyle("-fx-font-size: " + Ui.px(30) + "px;");
        VBox head = new VBox(Ui.px(8), Ui.eyebrow(editing == null ? "Crear" : "Editar"), h2);

        Field fName = new Field("Nombre del procedimiento", name);
        Field fMonths = new Field("Cada cuántos meses", months);
        GridPane g = new GridPane();
        g.setHgap(Ui.px(14));
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(Ui.px(150));
        g.getColumnConstraints().addAll(c1, c2);
        g.add(fName, 0, 0);
        g.add(fMonths, 1, 0);

        Field fTemplate = new Field("Mensaje predeterminado de WhatsApp", template);
        Label insertLabel = Ui.label("Insertar:", "muted", "fs13");
        insertLabel.setMinWidth(Region.USE_PREF_SIZE);
        FlowPane inserts = new FlowPane(Ui.px(8), Ui.px(8), insertLabel);
        inserts.setAlignment(Pos.CENTER_LEFT);
        for (String v : List.of("{nombre}", "{tipo}", "{procedimiento}", "{fecha}")) {
            Button ch = new Button(v);
            ch.getStyleClass().setAll("button", "ch");
            ch.setMnemonicParsing(false);
            ch.setMinWidth(Region.USE_PREF_SIZE);
            ch.setOnAction(e -> {
                template.insertText(template.getCaretPosition(), v);
                template.requestFocus();
            });
            inserts.getChildren().add(ch);
        }
        fTemplate.below(inserts);
        fTemplate.below(Ui.wrap("{fecha} incluye el día y la hora de la cita.", "muted", "fs13"));
        VBox.setMargin(inserts, new javafx.geometry.Insets(Ui.px(4), 0, 0, 0));

        VBox pv = new VBox(Ui.px(2), Ui.label("Así lo verá el paciente:", "muted", "fs14"), preview);
        pv.getStyleClass().add("soft-box");

        Button save = Ui.btn(editing == null ? "Crear procedimiento" : "Guardar cambios");
        save.getStyleClass().add("lg");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(e -> save(fName, fMonths, fTemplate));
        card.getChildren().addAll(head, g, fTemplate, pv, save);
        if (editing != null) {
            Button cancel = Ui.btn2("Cancelar");
            cancel.setMaxWidth(Double.MAX_VALUE);
            cancel.setOnAction(e -> {
                editing = null;
                clearForm();
                render();
            });
            card.getChildren().add(cancel);
        }
        if (name.getText().isEmpty() && template.getText().isEmpty() && editing == null) {
            clearForm();
        }
        updatePreview();
        return card;
    }

    private void clearForm() {
        name.setText("");
        months.setText("1");
        template.setText(MessageTemplates.DEFAULT_TEMPLATE);
    }

    private void startEdit(Process p) {
        editing = p;
        name.setText(p.name());
        months.setText(String.valueOf(p.monthsToSecond()));
        template.setText(p.messageTemplate());
        render();
        name.requestFocus();
    }

    private Integer monthsValue() {
        try {
            return Texts.isBlank(months.getText()) ? null : Integer.parseInt(months.getText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updatePreview() {
        preview.setText(s().processes().preview(template.getText(), name.getText(), monthsValue()));
    }

    private void save(Field fName, Field fMonths, Field fTemplate) {
        fName.error(null);
        fMonths.error(null);
        fTemplate.error(null);
        try {
            if (editing == null) {
                Process p = s().processes().create(name.getText(), monthsValue(), template.getText());
                shell().toast("Procedimiento creado: " + p.name() + ".");
            } else {
                Process p = s().processes().update(editing.id(), name.getText(), monthsValue(), template.getText());
                shell().toast("Cambios guardados en " + p.name() + ".");
            }
            editing = null;
            clearForm();
            render();
            shell().refreshStrip();
        } catch (ValidationException ex) {
            fName.error(ex.fieldErrors().get("name"));
            fMonths.error(ex.fieldErrors().get("months"));
            fTemplate.error(ex.fieldErrors().get("template"));
        }
    }
}
