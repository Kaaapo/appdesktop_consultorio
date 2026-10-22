package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.app.ui.DateField;
import co.sv.consultorio.app.ui.Field;
import co.sv.consultorio.app.ui.FlexRow;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.ValidationException;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.service.MessageTemplates;
import co.sv.consultorio.service.PatientInput;
import co.sv.consultorio.util.Fmt;
import co.sv.consultorio.util.Texts;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Nuevo paciente / editar paciente. */
public final class PacienteFormPage extends Page {

    private final Long patientId;
    private Patient current;

    private final TextField fullName = new TextField();
    private final TextField document = new TextField();
    private final TextField country = new TextField();
    private final TextField phone = new TextField();
    private final VBox procedures = new VBox(Ui.px(8));
    private final DateField birthDate = new DateField(null);
    private final Label age = Ui.label("", "hint");

    private Field fName;
    private Field fDoc;
    private Field fPhone;
    private Field fProcess;
    private Field fBirth;
    private final VBox dupBox = new VBox(Ui.px(4));
    private final VBox hero = Ui.card("hero");

    public PacienteFormPage(AppContext ctx, Long patientId) {
        super(ctx);
        this.patientId = patientId;
    }

    @Override
    public Shell.Nav nav() {
        return Shell.Nav.PACIENTES;
    }

    @Override
    public void refresh() {
        updateHero();
    }

    @Override
    public void render() {
        current = patientId == null ? null : s().patients().get(patientId);
        boolean edit = current != null;

        Label h1 = Ui.h1(edit ? "Editar paciente" : "Nuevo paciente");
        VBox.setMargin(h1, new javafx.geometry.Insets(Ui.px(10), 0, Ui.px(8), 0));
        String leadText = edit
                ? "Registrado el " + Fmt.longDateYear(current.registeredAt().toLocalDate())
                + (current.active() ? "." : " · inactivo.") + " Puede marcar varios procedimientos."
                : "Los campos son obligatorios. Puede marcar varios procedimientos; en la cita se elige cuál.";
        VBox left = new VBox(Ui.eyebrow("Pacientes"), h1, Ui.lead(leadText));
        Button cancel = Ui.btn2("Cancelar");
        cancel.setOnAction(e -> shell().go(Shell.Nav.PACIENTES));
        HBox hd = new HBox(Ui.px(16), Ui.hgrow(left), cancel);
        hd.setAlignment(Pos.BOTTOM_LEFT);

        VBox form = buildForm();
        form.setMinWidth(Ui.px(480));
        hero.setMinWidth(Ui.px(260));
        hero.setSpacing(Ui.px(14));
        hero.setStyle("-fx-padding: " + Ui.px(26) + "px;");
        FlexRow body = new FlexRow(Ui.px(20)).add(form, Ui.px(560), 2).add(hero, Ui.px(300), 1);

        main.getChildren().setAll(hd, body);
        fill();
        updateHero();
        Platform.runLater(fullName::requestFocus);
    }

    private VBox buildForm() {
        fName = new Field("Nombre completo", fullName);
        fDoc = new Field("Número de CC", document).hint("Solo números: los puntos y espacios se quitan solos.");
        fDoc.below(dupBox);
        country.setPromptText("57");
        country.setText("57");
        country.setPrefWidth(Ui.px(72));
        country.setTextFormatter(new TextFormatter<String>(c -> {
            if (c.isContentChange()) {
                String digits = Texts.digitsOnly(c.getText());
                c.setText(digits.length() > 4 ? digits.substring(0, 4) : digits);
            }
            return c;
        }));
        phone.setPromptText("300 000 0000");
        HBox.setHgrow(phone, Priority.ALWAYS);
        HBox phoneRow = new HBox(Ui.px(8), country, phone);
        phoneRow.setAlignment(Pos.CENTER_LEFT);
        fPhone = new Field("Indicativo y teléfono", phoneRow)
                .hint("En la lista se ve solo el número. WhatsApp usa el indicativo. Colombia: 57 y 10 dígitos.");

        document.setTextFormatter(new TextFormatter<String>(c -> {
            if (c.isContentChange()) {
                c.setText(Texts.digitsOnly(c.getText()));
            }
            return c;
        }));
        document.textProperty().addListener((o, a, b) -> checkDuplicate());
        fullName.textProperty().addListener((o, a, b) -> updateHero());
        birthDate.valueProperty().addListener((o, a, b) -> refreshAge());
        fBirth = new Field("Fecha de nacimiento", birthDate).below(age);

        procedures.getChildren().clear();
        List<Long> marked = current == null ? List.of()
                : s().patients().overview(current.id()).procedures().stream().map(Process::id).toList();
        for (Process p : s().processes().list()) {
            CheckBox box = new CheckBox(p.name() + " · " + p.monthsLabel());
            box.setUserData(p.id());
            box.setSelected(marked.contains(p.id()));
            box.getStyleClass().add("proc-check");
            box.selectedProperty().addListener((o, a, b) -> updateHero());
            procedures.getChildren().add(box);
        }
        if (procedures.getChildren().isEmpty()) {
            procedures.getChildren().add(Ui.wrap("Primero crea un procedimiento.", "hint"));
        }
        Button create = Ui.link("Crear procedimiento");
        create.getStyleClass().addAll("plain", "sm");
        create.setStyle("-fx-font-size: " + Ui.px(13) + "px; -fx-underline: true;");
        create.setOnAction(e -> shell().go(Shell.Nav.PROCESOS));
        HBox missing = new HBox(Ui.px(4), Ui.label("¿Falta uno?", "hint"), create);
        missing.setAlignment(Pos.CENTER_LEFT);
        fProcess = new Field("Procedimientos", procedures).below(missing);

        GridPane g1 = grid();
        g1.add(fName, 0, 0, 2, 1);
        g1.add(fDoc, 0, 1);
        g1.add(fPhone, 1, 1);

        GridPane g2 = grid();
        g2.add(fBirth, 0, 0);
        g2.add(fProcess, 1, 0);
        g2.getStyleClass().add("sep-line");
        g2.setStyle("-fx-padding: " + Ui.px(20) + "px 0 0 0;");

        Button saveAssign = Ui.btn2("Guardar y asignar cita");
        saveAssign.setOnAction(e -> save(true));
        Button save = Ui.btn("Guardar paciente");
        save.setDefaultButton(true);
        save.setOnAction(e -> save(false));
        HBox actions = new HBox(Ui.px(10), saveAssign, save);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("sep-line");
        actions.setStyle("-fx-padding: " + Ui.px(20) + "px 0 0 0;");

        VBox form = Ui.card();
        form.setSpacing(Ui.px(20));
        form.setStyle("-fx-padding: " + Ui.px(26) + "px;");
        form.getChildren().addAll(g1, g2, actions);
        return form;
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(Ui.px(18));
        g.setVgap(Ui.px(18));
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            c.setHgrow(Priority.ALWAYS);
            c.setFillWidth(true);
            g.getColumnConstraints().add(c);
        }
        return g;
    }

    private void fill() {
        if (current != null) {
            fullName.setText(current.fullName());
            document.setText(current.document());
            country.setText(current.countryCode() == null || current.countryCode().isBlank() ? "57" : current.countryCode());
            phone.setText(Texts.formatPhone(current.phone()));
            birthDate.setValue(current.birthDate());
        }
        refreshAge();
        checkDuplicate();
    }

    private void refreshAge() {
        Integer years = birthDate.getValue() == null ? null
                : java.time.Period.between(birthDate.getValue(), s().time().today()).getYears();
        if (birthDate.getValue() == null) {
            age.setText("La edad se calcula con esta fecha.");
        } else if (birthDate.getValue().isAfter(s().time().today())) {
            age.setText("Esa fecha es futura.");
        } else {
            age.setText(years == 1 ? "Edad: 1 año." : "Edad: " + years + " años.");
        }
    }

    private void checkDuplicate() {
        dupBox.getChildren().clear();
        dupBox.getStyleClass().remove("alert-pend");
        String doc = document.getText();
        if (doc == null || doc.length() < 4) {
            return;
        }
        Patient dup = s().patients().duplicateOf(doc, patientId == null ? 0 : patientId);
        if (dup == null) {
            return;
        }
        dupBox.getStyleClass().add("alert-pend");
        Label l = Ui.wrap("Ya existe un paciente con este documento: " + dup.fullName()
                + (dup.active() ? "." : " (inactivo)."), "w500");
        Button see = Ui.link("Ver paciente");
        see.setOnAction(e -> shell().goPaciente(dup.id()));
        dupBox.getChildren().addAll(l, see);
        VBox.setMargin(dupBox, new javafx.geometry.Insets(Ui.px(2), 0, 0, 0));
    }

    private void updateHero() {
        hero.getChildren().clear();
        List<Process> chosen = selectedProcedures();
        String name = Texts.isBlank(fullName.getText()) ? "{nombre}" : Texts.cleanName(fullName.getText());
        hero.getChildren().add(Ui.eyebrow("Cómo se cuentan las citas"));
        if (chosen.isEmpty()) {
            hero.getChildren().addAll(Ui.h2("Elige el procedimiento"),
                    Ui.wrap("Puede marcar varios. Al asignar la cita se elige de cuál es, y el aviso usa el mensaje de ese procedimiento.",
                            "muted", "fs15"));
            return;
        }
        if (chosen.size() > 1) {
            hero.getChildren().add(Ui.h2(chosen.size() + " procedimientos"));
            hero.getChildren().add(Ui.wrap(chosen.stream()
                    .map(p -> p.name() + " · cada " + p.monthsLabel())
                    .collect(java.util.stream.Collectors.joining("\n")), "muted", "fs15"));
            return;
        }
        Process proc = chosen.get(0);
        hero.getChildren().add(Ui.h2("Cada " + proc.monthsLabel()));
        Label text = Ui.wrap("Después de cada cita, la siguiente se sugiere " + proc.monthsLabel()
                + " después. Un control no suma a ese número. El aviso por WhatsApp sale solo, sin configurar nada.",
                "muted", "fs15");
        text.setLineSpacing(Ui.px(3));
        String msg = MessageTemplates.render(proc.messageTemplate(), name, proc.name(), "la fecha y la hora de la cita");
        Label preview = Ui.wrap(msg, "fs15");
        preview.setLineSpacing(Ui.px(3));
        VBox box = new VBox(preview);
        box.getStyleClass().add("hero-field");
        hero.getChildren().addAll(text, box, Ui.label("Mensaje de " + proc.name(), "muted", "fs13"));
    }

    private void save(boolean thenAssign) {
        for (Field f : List.of(fName, fDoc, fPhone, fBirth, fProcess)) {
            f.error(null);
        }
        birthDate.commitText();
        PatientInput in = new PatientInput(fullName.getText(), document.getText(), phone.getText(),
                selectedProcedureIds(), birthDate.getValue(), country.getText());
        try {
            Patient saved = current == null ? s().patients().create(in) : s().patients().update(current.id(), in);
            shell().toast(current == null ? saved.fullName() + " quedó registrado." : "Cambios guardados.");
            if (thenAssign) {
                shell().goCitas(saved.id());
            } else {
                shell().go(Shell.Nav.PACIENTES);
            }
        } catch (ValidationException ex) {
            Map<String, String> e = ex.fieldErrors();
            fName.error(e.get("fullName"));
            fDoc.error(e.get("document"));
            fPhone.error(e.get("countryCode") != null ? e.get("countryCode") : e.get("phone"));
            fBirth.error(e.get("birthDate"));
            fProcess.error(e.get("processId"));
            shell().error("Revisa los campos marcados.");
            focusFirstError(e);
        } catch (BusinessException ex) {
            shell().error(ex.getMessage());
        }
    }

    private void focusFirstError(Map<String, String> e) {
        Node target = e.containsKey("fullName") ? fullName
                : e.containsKey("document") ? document
                : e.containsKey("countryCode") ? country : e.containsKey("phone") ? phone
                : e.containsKey("birthDate") ? birthDate : procedures;
        target.requestFocus();
    }

    private List<Long> selectedProcedureIds() {
        List<Long> ids = new ArrayList<>();
        for (Node n : procedures.getChildren()) {
            if (n instanceof CheckBox box && box.isSelected() && box.getUserData() instanceof Long id) {
                ids.add(id);
            }
        }
        return ids;
    }

    private List<Process> selectedProcedures() {
        List<Process> out = new ArrayList<>();
        for (Long id : selectedProcedureIds()) {
            Process p = s().processes().get(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }
}
