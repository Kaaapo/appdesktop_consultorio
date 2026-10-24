package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.app.dialogs.WhatsAppFlow;
import co.sv.consultorio.app.ui.Chip;
import co.sv.consultorio.app.ui.Choice;
import co.sv.consultorio.app.ui.DateField;
import co.sv.consultorio.app.ui.Field;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.Motion;
import co.sv.consultorio.app.ui.Switch;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.NextStep;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.service.AppointmentService;
import co.sv.consultorio.service.PatientFilter;
import co.sv.consultorio.service.PatientService;
import co.sv.consultorio.util.Fmt;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Calendario mensual y formulario para asignar una cita o un control. */
public final class CitasPage extends Page {

    private static final int SHOWN_PATIENTS = 4;
    private YearMonth month;
    private LocalDate day;
    private LocalTime slot;
    private String query = "";
    private boolean includeWithAppointment;
    private Long patientId;
    private AppointmentType type = AppointmentType.CITA;
    private AppointmentService.AssignResult last;
    private String lastText;

    private Long replaceId;
    private Long procedureId;

    private List<PatientOverview> overviews = List.of();
    private final VBox patientList = new VBox(Ui.px(6));
    private final VBox formBox = new VBox();
    private final VBox rightBox = new VBox(Ui.px(22));
    private DateField dateField;
    private FlowPane hoursPane;
    private Label hoursTitle;
    private Label hoursNote;
    private Button sendBtn;
    private Button onlyBtn;
    private Label needHint;
    private GridPane calendarGrid;
    private Node dayCard;
    private boolean syncingDate;

    public CitasPage(AppContext ctx, Long patientId) {
        super(ctx);
        this.patientId = patientId;
        LocalDate today = s().time().today();
        LocalDate start = today;
        if (patientId != null) {
            PatientOverview o = s().patients().overview(patientId);
            if (o != null) {
                if (o.hasAppointment()) {
                    includeWithAppointment = true;
                    type = o.appointment().type();
                } else {
                    NextStep step = o.nextStep(today);
                    type = step.type();
                    if (step.date() != null && step.date().isAfter(today)) {
                        start = step.date();
                    }
                }
            }
        }
        day = s().appointments().firstFreeDayFrom(start);
        month = YearMonth.from(day);
    }

    /** Elige el día antes de dibujar la pantalla (fecha sugerida). */
    public void prepareDay(LocalDate day) {
        if (day == null) {
            return;
        }
        this.day = day.isBefore(s().time().today()) ? s().time().today() : day;
        month = YearMonth.from(this.day);
        includeWithAppointment = true;
    }

    /** La próxima asignación mueve esta cita, no crea otra. */
    public void prepareReassign(long appointmentId) {
        replaceId = appointmentId;
        Appointment current = s().appointments().get(appointmentId);
        if (current == null) {
            return;
        }
        patientId = current.patientId();
        type = current.type();
        includeWithAppointment = true;
        prepareDay(current.startsAt().toLocalDate());
    }

    @Override
    public Shell.Nav nav() {
        return Shell.Nav.CITAS;
    }

    @Override
    public void refresh() {
        overviews = s().patients().overviews().stream().filter(o -> o.patient().active()).toList();
        if (patientId != null && overviews.stream().noneMatch(o -> o.patient().id() == patientId)) {
            patientId = null;
        }
        renderForm();
        renderRight();
        Motion.settle(formBox);
        Motion.settle(rightBox);
    }

    @Override
    public void render() {
        overviews = s().patients().overviews().stream().filter(o -> o.patient().active()).toList();
        if (patientId != null && overviews.stream().noneMatch(o -> o.patient().id() == patientId)) {
            patientId = null;
        }

        VBox left = new VBox(Ui.px(8), Ui.h1("Citas"), Ui.lead("Asigna una cita o un control. El programa cuenta cuántas citas lleva cada paciente."));
        HBox hd = new HBox(Ui.px(16), Ui.hgrow(left));
        hd.setAlignment(Pos.BOTTOM_LEFT);

        formBox.getStyleClass().setAll("card", "hero", "shadow");
        formBox.setSpacing(Ui.px(18));
        formBox.setStyle("-fx-padding: " + Ui.px(28) + "px;");
        formBox.setPrefWidth(Ui.px(380));
        formBox.setMinWidth(Ui.px(340));
        HBox.setHgrow(formBox, Priority.ALWAYS);
        rightBox.setPrefWidth(Ui.px(380));
        rightBox.setMinWidth(Ui.px(340));
        HBox.setHgrow(rightBox, Priority.ALWAYS);

        HBox body = new HBox(Ui.px(22), formBox, rightBox);
        body.setAlignment(Pos.TOP_LEFT);
        body.setFillHeight(false);
        main.getChildren().setAll(hd, body);
        renderForm();
        renderRight();
    }

    // ------------------------------------------------------------------ formulario

    private void renderForm() {
        LocalDate today = s().time().today();
        PatientOverview sel = selected();
        if (type == null) {
            type = sel != null && sel.hasAppointment() ? sel.appointment().type() : AppointmentType.CITA;
        }

        Label h2 = Ui.h2("Asignar cita");
        h2.setStyle("-fx-font-size: " + Ui.px(34) + "px;");
        VBox head = new VBox(Ui.px(8), Ui.eyebrow("Nueva cita"), h2);

        TextField search = new TextField(query);
        search.setPromptText("Buscar por nombre o documento");
        search.getStyleClass().addAll("search", "tall");
        PauseTransition debounce = new PauseTransition(Duration.millis(150));
        debounce.setOnFinished(e -> {
            query = search.getText();
            renderPatients(true);
        });
        search.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        StackPane searchBox = Ui.withLeadingIcon(search, Icons.SEARCH);
        Label pl = Ui.label("Paciente", "lbl");
        pl.setLabelFor(search);

        Switch withAppt = new Switch("Incluir pacientes que ya tienen cita");
        withAppt.setSelected(includeWithAppointment);
        withAppt.selectedProperty().addListener((o, a, b) -> {
            includeWithAppointment = b;
            renderPatients(true);
        });
        VBox patientBlock = new VBox(Ui.px(6), pl, searchBox, patientList, withAppt, selectedBox(sel, today));
        VBox.setMargin(patientList, new javafx.geometry.Insets(Ui.px(4), 0, 0, 0));
        syncProcedure(sel);
        Process chosen = procedureId == null ? null : s().processes().get(procedureId);
        if (sel != null && chosen != null) {
            LocalDate suggested = s().appointments().suggestedDay(sel, chosen, today);
            if (suggested != null) {
                Button use = Ui.link("Usar el " + Fmt.longDate(suggested));
                use.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                use.setWrapText(true);
                use.setOnAction(e -> selectDay(suggested));
                Label note = Ui.oneLine("Siguiente cita sugerida por " + chosen.name()
                        + " (cada " + chosen.monthsLabel() + ").", "fs14");
                note.setMinWidth(0);
                VBox hint = new VBox(Ui.px(4), note, use);
                hint.setFillWidth(true);
                patientBlock.getChildren().add(hint);
            }
        }
        if (sel != null && replaceId != null) {
            patientBlock.getChildren().add(Ui.wrap(
                    "Al reasignar, el aviso anterior deja de valer y podrás avisar de la nueva fecha.", "muted", "fs14"));
        }
        renderPatients();

        Choice<AppointmentType> typeBox = new Choice<>(List.of(
                Choice.opt("Cita", AppointmentType.CITA),
                Choice.opt("Control", AppointmentType.CONTROL)), type, v -> type = v);
        typeBox.getStyleClass().add("tall");
        dateField = new DateField(day).blockSundays();
        dateField.getStyleClass().add("tall");
        dateField.valueProperty().addListener((o, a, b) -> {
            if (!syncingDate && b != null && !b.equals(day)) {
                selectDay(b);
            }
        });
        GridPane g = new GridPane();
        g.setHgap(Ui.px(12));
        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(50);
        g.getColumnConstraints().addAll(c, c);
        g.add(new Field("Tipo de cita", typeBox), 0, 0);
        g.add(new Field("Fecha", dateField), 1, 0);
        if (sel != null) {
            g.add(procedureField(sel), 0, 1, 2, 1);
        }

        hoursTitle = Ui.label("", "lbl");
        hoursPane = flow();
        hoursNote = Ui.label("", "muted", "fs13");
        hoursNote.setWrapText(true);
        refreshHours();
        VBox when = new VBox(Ui.px(6), hoursTitle, hoursPane, hoursNote);

        sendBtn = Ui.button("hbtn", "Asignar y enviar WhatsApp", Icons.CHAT);
        sendBtn.setMaxWidth(Double.MAX_VALUE);
        onlyBtn = Ui.button("hbtn2", "Solo asignar la cita", null);
        onlyBtn.setMaxWidth(Double.MAX_VALUE);
        sendBtn.setOnAction(e -> assign(true));
        onlyBtn.setOnAction(e -> assign(false));
        needHint = Ui.wrap("", "muted", "fs14");
        VBox actions = new VBox(Ui.px(10), sendBtn, onlyBtn, needHint);
        VBox.setMargin(actions, new javafx.geometry.Insets(Ui.px(6), 0, 0, 0));
        refreshActions();
        if (last != null) {
            Label t = Ui.wrap((last.rescheduled() ? "Cita reasignada. " : "Cita asignada. ") + lastText, "fs15");
            Button undo = Ui.button("hbtn2", "Deshacer", Icons.UNDO);
            undo.getStyleClass().add("sm");
            undo.setOnAction(e -> undo());
            HBox status = new HBox(Ui.px(10), Ui.hgrow(t), undo);
            status.getStyleClass().add("hero-status");
            undo.setMinWidth(Region.USE_PREF_SIZE);
            actions.getChildren().add(status);
        }

        formBox.getChildren().setAll(head, patientBlock, g, when, actions);
    }

    private static FlowPane flow() {
        FlowPane f = new FlowPane(Ui.px(8), Ui.px(8));
        f.setPrefWrapLength(Ui.px(300));
        return f;
    }

    private static Button slotButton(String text, boolean on, boolean busy) {
        Button b = new Button(text);
        b.getStyleClass().setAll("button", "slot");
        b.setMnemonicParsing(false);
        if (on) {
            b.getStyleClass().add("on");
        }
        if (busy) {
            b.getStyleClass().add("bz");
            b.setFocusTraversable(false);
        }
        return b;
    }

    private void refreshHours() {
        if (hoursPane == null) {
            return;
        }
        List<AppointmentService.Slot> slots = s().appointments().daySlots(day, replaceId == null ? 0 : replaceId);
        long free = slots.stream().filter(AppointmentService.Slot::free).count();
        if (slot != null && slots.stream().noneMatch(sl -> sl.time().equals(slot) && sl.free())) {
            slot = null;
        }
        hoursPane.getChildren().clear();
        for (AppointmentService.Slot sl : slots) {
            Button b = slotButton(Fmt.time12(sl.time()), sl.time().equals(slot), !sl.free());
            b.setUserData(sl.time());
            if (sl.free()) {
                b.setOnAction(e -> selectSlot(sl.time()));
            } else {
                Ui.tip(b, sl.state() == AppointmentService.SlotState.PAST ? "Esa hora ya pasó"
                        : replaceId != null && sl.occupant() != null && sl.occupant().id() == replaceId
                        ? "Es la hora actual de la cita que vas a reasignar"
                        : sl.occupant() != null && sl.occupant().type() == AppointmentType.CONTROL
                        ? "Esa hora ya tiene un control"
                        : "Es la media hora siguiente a una cita");
            }
            hoursPane.getChildren().add(b);
        }
        hoursTitle.setText("Hora · " + Fmt.count((int) free, "libre", "libres") + " el " + Fmt.longDateDow(day));
        hoursNote.setText(slots.isEmpty()
                ? (day.getDayOfWeek() == DayOfWeek.SUNDAY
                ? "El domingo el consultorio no abre."
                : "Este día el consultorio no atiende.")
                : free == 0 ? "No quedan horas libres este día. Elige otra fecha."
                : "Una cita tapa su hora y la media hora siguiente. Un control solo ocupa su hora.");
        refreshActions();
    }

    private void selectSlot(LocalTime time) {
        slot = time;
        paintHourSelection();
        refreshActions();
    }

    private void paintHourSelection() {
        if (hoursPane == null) {
            return;
        }
        for (Node n : hoursPane.getChildren()) {
            if (!(n instanceof Button b) || !(b.getUserData() instanceof LocalTime time)) {
                continue;
            }
            if (time.equals(slot)) {
                if (!b.getStyleClass().contains("on")) {
                    b.getStyleClass().add("on");
                }
            } else {
                b.getStyleClass().remove("on");
            }
        }
    }

    private void refreshActions() {
        if (sendBtn == null) {
            return;
        }
        PatientOverview sel = selected();
        boolean ready = sel != null && procedureId != null && slot != null;
        sendBtn.setDisable(!ready);
        onlyBtn.setDisable(!ready);
        needHint.setText(ready ? "" : sel == null
                ? "Elige un paciente para asignarle la cita."
                : procedureId == null
                ? "Elige el procedimiento de la cita."
                : "Elige una hora libre para asignar la cita.");
        needHint.setVisible(!ready);
        needHint.setManaged(!ready);
    }

    private PatientOverview selected() {
        if (patientId == null) {
            return null;
        }
        return overviews.stream().filter(o -> o.patient().id() == patientId).findFirst().orElse(null);
    }

    private void renderPatients() {
        renderPatients(false);
    }

    private void renderPatients(boolean animate) {
        LocalDate today = s().time().today();
        PatientFilter f = new PatientFilter(query, null,
                includeWithAppointment ? PatientFilter.CitaFilter.ALL : PatientFilter.CitaFilter.WITHOUT,
                PatientFilter.WhatsAppFilter.ALL, null, null, false, false);
        List<PatientOverview> matches = new ArrayList<>(PatientService.filter(overviews, f));
        matches.sort(Comparator.comparing((PatientOverview o) -> o.hasAppointment())
                .thenComparing(o -> o.hasAppointment() ? o.appointment().startsAt().toLocalDate() : o.nextStep(today).date(),
                        Comparator.nullsLast(Comparator.naturalOrder())));
        List<PatientOverview> shown = new ArrayList<>(matches.stream().limit(SHOWN_PATIENTS).toList());
        PatientOverview sel = selected();
        if (sel != null && shown.stream().noneMatch(o -> o.patient().id() == sel.patient().id())
                && (query.isBlank() || matches.contains(sel))) {
            shown.add(0, sel);
            if (shown.size() > SHOWN_PATIENTS) {
                shown.remove(shown.size() - 1);
            }
        }
        patientList.getChildren().clear();
        for (PatientOverview o : shown) {
            patientList.getChildren().add(patientCard(o, today));
        }
        if (shown.isEmpty()) {
            patientList.getChildren().add(Ui.wrap(overviews.isEmpty()
                    ? "Aún no hay pacientes. Regístralos en Pacientes."
                    : includeWithAppointment || !query.isBlank()
                    ? "Sin resultados. Prueba con otro nombre o documento."
                    : "Todos los pacientes tienen cita. Activa “Incluir pacientes que ya tienen cita” para asignar otra.",
                    "muted", "fs14"));
        }
        int more = matches.size() - (int) shown.stream().filter(matches::contains).count();
        if (more > 0) {
            patientList.getChildren().add(Ui.label("y " + more + " más · escribe para filtrar", "muted", "fs13"));
        }
        if (animate) {
            Motion.settle(patientList);
        }
    }

    private Node patientCard(PatientOverview o, LocalDate today) {
        String proc = o.procedureNames().isBlank() ? "" : " · " + o.procedureNames();
        String sub = "CC " + o.patient().document() + proc + " · "
                + (o.citaCount() == 1 ? "1 cita" : o.citaCount() + " citas")
                + (o.hasAppointment() ? "" : " · " + o.nextStep(today).note(o.nextCitaNumber()));
        Label nm = Ui.oneLine(o.patient().fullName(), "nm");
        Label sb = Ui.oneLine(sub, "sub");
        VBox text = new VBox(Ui.px(1), nm, sb);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);

        HBox cita = o.hasAppointment()
                ? Chip.conCita("Cita " + Fmt.appointmentInline(o.appointment().startsAt(), today))
                : Chip.sinCita();
        java.time.LocalDate avisadoEl = o.appointment() == null || o.appointment().notifiedAt() == null
                ? null : o.appointment().notifiedAt().toLocalDate();
        HBox av = Chip.whatsApp(o.waStatus(), avisadoEl);
        cita.getStyleClass().add("sm");
        av.getStyleClass().add("sm");
        javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane(Ui.px(6), Ui.px(6), cita, av);
        chips.setAlignment(Pos.CENTER_LEFT);

        VBox b = new VBox(Ui.px(8), text, chips);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().add("pr2");
        if (patientId != null && patientId == o.patient().id()) {
            b.getStyleClass().add("on");
        }
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
        b.setAccessibleText(o.patient().fullName());
        Ui.clickable(b, () -> shell().keepScroll(() -> {
            patientId = o.patient().id();
            type = o.hasAppointment() ? o.appointment().type() : o.nextStep(today).type();
            if (!o.hasAppointment()) {
                NextStep step = o.nextStep(today);
                if (step.date() != null && step.date().isAfter(today) && slot == null) {
                    selectDayQuiet(s().appointments().firstFreeDayFrom(step.date()));
                }
            }
            renderForm();
            renderRight();
            Motion.settle(formBox);
            Motion.settle(rightBox);
        }));
        return b;
    }

    private Node selectedBox(PatientOverview sel, LocalDate today) {
        TextFlow tf = new TextFlow();
        if (sel == null) {
            tf.getChildren().add(text("Elige un paciente de la lista.", "tf-m"));
        } else {
            String note = sel.hasAppointment()
                    ? "tiene cita " + Fmt.appointmentInline(sel.appointment().startsAt(), today)
                    : sel.nextStep(today).note(sel.nextCitaNumber());
            String av = switch (sel.waStatus()) {
                case NONE -> "sin avisar por WhatsApp";
                case AVISADO -> "avisado, sin respuesta";
                case AGENDADO -> "agendado: ya respondió";
                case NO_RESPONDIO -> "no respondió, se le puede escribir de nuevo";
            };
            tf.getChildren().addAll(text("Seleccionado ", "tf-m"), text(sel.patient().fullName(), "tf-b"),
                    text(" · ", null), text(note, "tf-m"), text(" · ", null), text(av, "tf-b"));
        }
        tf.setLineSpacing(Ui.px(2));
        VBox box = new VBox(tf);
        box.getStyleClass().add("hero-note");
        VBox.setMargin(box, new javafx.geometry.Insets(Ui.px(2), 0, 0, 0));
        return box;
    }

    private static Text text(String s, String cls) {
        Text t = new Text(Ui.keepTimes(s));
        t.getStyleClass().add("tf");
        if (cls != null) {
            t.getStyleClass().add(cls);
        }
        return t;
    }

    private void selectDayQuiet(LocalDate d) {
        day = d;
        month = YearMonth.from(d);
        slot = null;
    }

    private void selectDay(LocalDate d) {
        if (d == null) {
            return;
        }
        if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            shell().toast("El domingo el consultorio no abre.");
            if (dateField != null && (dateField.getValue() == null || !day.equals(dateField.getValue()))) {
                syncingDate = true;
                dateField.setValue(day);
                syncingDate = false;
            }
            return;
        }
        boolean sameDay = d.equals(day);
        boolean sameMonth = YearMonth.from(d).equals(month) && calendarGrid != null;
        if (sameDay && sameMonth) {
            return;
        }
        shell().keepScroll(() -> {
            if (sameDay) {
                month = YearMonth.from(d);
            } else {
                selectDayQuiet(d);
            }
            if (dateField != null && (dateField.getValue() == null || !d.equals(dateField.getValue()))) {
                syncingDate = true;
                dateField.setValue(d);
                syncingDate = false;
            }
            if (!sameDay) {
                refreshHours();
            }
            if (sameMonth) {
                paintCalendarSelection();
                if (!sameDay) {
                    replaceDayDetail();
                    Motion.settle(dayCard);
                }
            } else {
                renderRight();
                Motion.settle(rightBox);
            }
            if (!sameDay) {
                Motion.settle(hoursPane);
            }
        });
    }

    private void syncProcedure(PatientOverview sel) {
        List<Process> options = procedureOptions(sel);
        boolean valid = procedureId != null && options.stream().anyMatch(p -> p.id() == procedureId);
        if (valid) {
            return;
        }
        if (replaceId != null) {
            Appointment current = s().appointments().get(replaceId);
            if (current != null && current.processId() != null
                    && options.stream().anyMatch(p -> p.id() == current.processId())) {
                procedureId = current.processId();
                return;
            }
        }
        procedureId = options.size() == 1 ? options.get(0).id() : null;
    }

    private List<Process> procedureOptions(PatientOverview sel) {
        if (sel != null && !sel.procedures().isEmpty()) {
            return sel.procedures();
        }
        return s().processes().list();
    }

    private Field procedureField(PatientOverview sel) {
        List<Process> options = procedureOptions(sel);
        List<Choice.Opt<Long>> opts = new ArrayList<>();
        for (Process p : options) {
            opts.add(Choice.opt(p.name() + " · " + p.monthsLabel(), p.id()));
        }
        Choice<Long> box = new Choice<>(opts, procedureId, v -> {
            procedureId = v;
            renderForm();
        });
        box.getStyleClass().add("tall");
        box.setPromptText(opts.isEmpty() ? "Primero crea un procedimiento" : "Elige el procedimiento");
        String hint = sel.procedures().isEmpty()
                ? "Este paciente no tiene procedimientos. El que elijas se le asigna."
                : "La cita y el mensaje de WhatsApp quedan de este procedimiento.";
        return new Field("Procedimiento", box).hint(hint);
    }

    private void assign(boolean thenWhatsApp) {
        PatientOverview sel = selected();
        if (sel == null || slot == null) {
            return;
        }
        try {
            LocalDateTime when = day.atTime(slot);
            last = replaceId == null
                    ? s().appointments().add(sel.patient().id(), type, when, procedureId)
                    : s().appointments().reschedule(replaceId, type, when, procedureId);
            replaceId = null;
            lastText = sel.patient().fullName() + " · " + Fmt.longDateDow(day) + ", " + Fmt.time12(slot);
            slot = null;
            long apptId = last.saved().id();
            shell().changed();
            if (thenWhatsApp) {
                AppointmentView v = s().appointments().view(apptId);
                if (v != null) {
                    WhatsAppFlow.message(ctx, v, null);
                }
            } else {
                shell().toast((last.rescheduled() ? "Cita reasignada: " : "Cita asignada: ") + lastText);
            }
        } catch (BusinessException ex) {
            if (replaceId != null && s().appointments().get(replaceId) == null) {
                replaceId = null;
            }
            shell().error(ex.getMessage());
            renderForm();
        }
    }

    private void undo() {
        if (last == null) {
            return;
        }
        try {
            s().appointments().undo(last);
            shell().toast(last.rescheduled() ? "Se deshizo la reasignación." : "Se deshizo la cita asignada.");
        } catch (BusinessException ex) {
            shell().error(ex.getMessage());
        }
        last = null;
        shell().changed();
    }

    // ------------------------------------------------------------------ derecha

    private void renderRight() {
        dayCard = dayDetail();
        rightBox.getChildren().setAll(calendar(), dayCard);
    }

    private void replaceDayDetail() {
        dayCard = dayDetail();
        if (rightBox.getChildren().size() >= 2) {
            rightBox.getChildren().set(1, dayCard);
        } else {
            rightBox.getChildren().setAll(calendar(), dayCard);
        }
    }

    private void paintCalendarSelection() {
        if (calendarGrid == null) {
            return;
        }
        for (Node n : calendarGrid.getChildren()) {
            if (!(n instanceof Button b) || !(b.getUserData() instanceof LocalDate date)) {
                continue;
            }
            if (date.equals(day)) {
                if (!b.getStyleClass().contains("sel")) {
                    b.getStyleClass().add("sel");
                }
            } else {
                b.getStyleClass().remove("sel");
            }
        }
    }

    private Node calendar() {
        LocalDate today = s().time().today();
        Map<LocalDate, Integer> counts = s().appointments().monthCounts(month);

        Button prev = Ui.ghost(Icons.CHEVRON_LEFT, "Mes anterior", 20);
        prev.setOnAction(e -> shell().keepScroll(() -> {
            month = month.minusMonths(1);
            renderRight();
            Motion.settle(rightBox);
        }));
        Button next = Ui.ghost(Icons.CHEVRON_RIGHT, "Mes siguiente", 20);
        next.setOnAction(e -> shell().keepScroll(() -> {
            month = month.plusMonths(1);
            renderRight();
            Motion.settle(rightBox);
        }));
        Label title = Ui.h2(Fmt.capitalize(Fmt.monthName(month.getMonthValue())) + " " + month.getYear());
        title.setWrapText(false);
        title.setMinWidth(Region.USE_PREF_SIZE);
        HBox legend = new HBox(Ui.px(14), legend("Libre", null), legend("1–2 citas", "c1"), legend("3 o más", "c2"));
        legend.setAlignment(Pos.CENTER_LEFT);
        Button todayBtn = Ui.link("Hoy");
        todayBtn.setOnAction(e -> selectDay(today));
        todayBtn.setVisible(!month.equals(YearMonth.from(today)));
        HBox head = new HBox(Ui.px(4), title, Ui.hspacer(), todayBtn, prev, next);
        head.setAlignment(Pos.CENTER_LEFT);
        HBox.setMargin(todayBtn, new javafx.geometry.Insets(0, Ui.px(8), 0, 0));

        GridPane grid = new GridPane();
        calendarGrid = grid;
        grid.setHgap(Ui.px(6));
        grid.setVgap(Ui.px(6));
        for (int i = 0; i < 7; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / 7);
            c.setFillWidth(true);
            grid.getColumnConstraints().add(c);
        }
        String[] names = {"Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do"};
        for (int i = 0; i < 7; i++) {
            Label dh = Ui.label(Ui.spaced(names[i]), "dh");
            dh.setMaxWidth(Double.MAX_VALUE);
            grid.add(dh, i, 0);
        }
        int offset = month.atDay(1).getDayOfWeek().getValue() - 1;
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            int n = counts.getOrDefault(date, 0);
            int idx = offset + d - 1;
            boolean sunday = date.getDayOfWeek() == DayOfWeek.SUNDAY;
            Label dl = Ui.label(String.valueOf(d), "d");
            String mark = sunday ? (n == 0 ? "cerrado" : n == 1 ? "1 cita" : n + " citas")
                    : n == 0 ? "libre" : n == 1 ? "1 cita" : n + " citas";
            Label kl = Ui.label(mark, "k");
            VBox content = new VBox(Ui.px(4), dl, kl);
            content.setFillWidth(true);
            Button b = new Button();
            b.getStyleClass().setAll("button", "day");
            if (sunday) {
                b.getStyleClass().add("closed");
            } else if (n > 0) {
                b.getStyleClass().add(n >= 3 ? "c2" : "c1");
            }
            if (date.isBefore(today)) {
                b.getStyleClass().add("past");
            }
            if (date.equals(today)) {
                b.getStyleClass().add("today");
            }
            if (date.equals(day)) {
                b.getStyleClass().add("sel");
            }
            b.setGraphic(content);
            b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinWidth(0);
            content.maxWidthProperty().bind(b.widthProperty().subtract(Ui.px(14)));
            b.setUserData(date);
            b.setAccessibleText(Fmt.longDateDow(date) + ": " + kl.getText());
            b.setOnAction(e -> selectDay(date));
            grid.add(b, idx % 7, 1 + idx / 7);
        }
        VBox card = Ui.card();
        card.setSpacing(Ui.px(14));
        card.setStyle("-fx-padding: " + Ui.px(22) + "px;");
        card.getChildren().addAll(head, grid, legend);
        return card;
    }

    private static Node legend(String text, String cls) {
        Region sw = Ui.cls(new Region(), "sw");
        if (cls != null) {
            sw.getStyleClass().add(cls);
        }
        HBox h = new HBox(sw, new Label(text));
        h.getStyleClass().add("lg2");
        return h;
    }

    private Node dayDetail() {
        List<AppointmentView> list = s().appointments().between(day, day);
        VBox card = Ui.card();
        card.setStyle("-fx-padding: " + Ui.px(22) + "px;");
        Label h = Ui.h2("Citas del " + Fmt.longDateDow(day), "sm");
        card.getChildren().add(h);
        if (list.isEmpty()) {
            Label empty = Ui.label(day.getDayOfWeek() == DayOfWeek.SUNDAY
                    ? "El domingo el consultorio no abre."
                    : s().settings().get().workDays().contains(day.getDayOfWeek())
                    ? "Día libre, sin citas asignadas." : "Día no laboral, sin citas asignadas.", "muted");
            empty.setWrapText(true);
            VBox.setMargin(empty, new javafx.geometry.Insets(Ui.px(12), 0, 0, 0));
            card.getChildren().add(empty);
        }
        VBox rows = new VBox();
        VBox.setMargin(rows, new javafx.geometry.Insets(Ui.px(12), 0, 0, 0));
        for (AppointmentView v : list) {
            Label t = Ui.label(Fmt.time12(v.appointment().startsAt()), "w600");
            t.setMinWidth(Region.USE_PREF_SIZE);
            Node name = InicioPage.nameBlock(v.patient().fullName(), v.typeAndProcess());
            HBox row = new HBox(t, name, appointmentAction(v));
            row.getStyleClass().add("li-plain");
            rows.getChildren().add(row);
        }
        if (!list.isEmpty()) {
            card.getChildren().add(rows);
        }
        return card;
    }

    private Node appointmentAction(AppointmentView v) {
        Appointment a = v.appointment();
        Node n;
        if (!a.waStatus().canSend()) {
            LocalDate when = a.notifiedAt() == null ? null : a.notifiedAt().toLocalDate();
            n = Chip.clickable(Chip.whatsApp(a.waStatus(), when),
                    () -> WhatsAppFlow.forAppointment(ctx, v.appointment().id()), a.waStatus().label());
        } else if (a.startsAt().isBefore(s().time().now())) {
            n = Chip.of("comp", "Ya pasó", false);
        } else {
            n = Ui.wa(() -> WhatsAppFlow.forAppointment(ctx, a.id()));
        }
        if (n instanceof Region r) {
            r.setMinWidth(Region.USE_PREF_SIZE);
        }
        return n;
    }
}
