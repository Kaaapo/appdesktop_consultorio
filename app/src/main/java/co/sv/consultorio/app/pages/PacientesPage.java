package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.app.dialogs.ConfirmDialog;
import co.sv.consultorio.app.dialogs.WhatsAppFlow;
import co.sv.consultorio.app.ui.Chip;
import co.sv.consultorio.app.ui.Choice;
import co.sv.consultorio.app.ui.DateField;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.PopMenu;
import co.sv.consultorio.app.ui.Seg;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.service.PatientFilter;
import co.sv.consultorio.service.PatientService;
import co.sv.consultorio.util.Fmt;
import co.sv.consultorio.util.Texts;
import javafx.animation.PauseTransition;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public final class PacientesPage extends Page {

    private enum Range { HOY, SEMANA, MES }

    private static final int PAGE_SIZE = 8;
    private static final int COL_PROC = 5;
    /** Padding horizontal de .tr.fit (6+6) y -fx-hgap de 4 entre las 12 columnas. */
    private static final double PAD_X = 12;
    private static final double COL_GAP = 4;
    /**
     * Piso en px. No se baja de aquí: una fecha, una cédula o «Rejuvenecimiento»
     * tienen que caber. El nombre y el procedimiento crecen si sobra espacio.
     */
    private static final double[] COL_ABS = {70, 84, 72, 30, 86, 104, 48, 76, 132, 100, 52, 34};
    private static final double[] COL_NICE = {220, 110, 84, 36, 100, 180, 48, 76, 148, 120, 60, 36};

    private String query = "";
    private Long processId;
    private PatientFilter.CitaFilter cita = PatientFilter.CitaFilter.ALL;
    private PatientFilter.WhatsAppFilter wa;
    private LocalDate from;
    private LocalDate to;
    private boolean inactive;
    private int pageIndex;

    private List<PatientOverview> all = List.of();
    private final Label lead = Ui.lead("");
    private final VBox tableCard = Ui.card();
    private Seg<Range> rangeSeg;
    private DateField fromField;
    private DateField toField;
    private boolean syncingDates;

    public PacientesPage(AppContext ctx, PatientFilter.WhatsAppFilter preset) {
        super(ctx);
        this.wa = preset == null ? PatientFilter.WhatsAppFilter.ALL : preset;
    }

    @Override
    public Shell.Nav nav() {
        return Shell.Nav.PACIENTES;
    }

    @Override
    public void render() {
        all = s().patients().overviews();
        main.getChildren().setAll(header(), filters(), tableCard);
        renderTable();
    }

    @Override
    public void refresh() {
        all = s().patients().overviews();
        renderTable();
    }

    private Node header() {
        VBox left = new VBox(Ui.h1("Pacientes"), lead);
        Button nuevo = Ui.btn("Nuevo paciente", Icons.PLUS);
        nuevo.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        nuevo.setOnAction(e -> shell().goPaciente(null));
        HBox hd = new HBox(Ui.px(16), Ui.hgrow(left), nuevo);
        hd.setAlignment(Pos.BOTTOM_LEFT);
        return hd;
    }

    private Node filters() {
        TextField search = new TextField(query);
        search.setPromptText("Buscar por nombre o documento");
        search.getStyleClass().add("search");
        search.setAccessibleText("Buscar paciente");
        PauseTransition debounce = new PauseTransition(Duration.millis(180));
        debounce.setOnFinished(e -> {
            query = search.getText();
            pageIndex = 0;
            renderTable();
        });
        search.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        StackPane searchBox = Ui.withLeadingIcon(search, Icons.SEARCH);
        searchBox.setPrefWidth(Ui.px(260));
        searchBox.setMinWidth(Ui.px(180));
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        List<Choice.Opt<Long>> procOpts = new ArrayList<>();
        procOpts.add(Choice.opt("Todos", null));
        for (Process p : s().processes().list()) {
            procOpts.add(Choice.opt(p.name(), p.id()));
        }
        Choice<Long> proc = new Choice<>(procOpts, processId, v -> {
            processId = v;
            pageIndex = 0;
            renderTable();
        });
        proc.setAccessibleText("Procedimiento");
        Choice<PatientFilter.CitaFilter> citaBox = new Choice<>(List.of(
                Choice.opt("Cita: todas", PatientFilter.CitaFilter.ALL),
                Choice.opt("Con cita", PatientFilter.CitaFilter.WITH),
                Choice.opt("Sin cita", PatientFilter.CitaFilter.WITHOUT)), cita, v -> {
            cita = v;
            pageIndex = 0;
            renderTable();
        });
        citaBox.setAccessibleText("Filtrar por cita");
        Choice<PatientFilter.WhatsAppFilter> waBox = new Choice<>(List.of(
                Choice.opt("WhatsApp: todos", PatientFilter.WhatsAppFilter.ALL),
                Choice.opt("Sin avisar", PatientFilter.WhatsAppFilter.SIN_AVISAR),
                Choice.opt("Avisados", PatientFilter.WhatsAppFilter.AVISADO),
                Choice.opt("Agendados", PatientFilter.WhatsAppFilter.AGENDADO),
                Choice.opt("No respondieron", PatientFilter.WhatsAppFilter.NO_RESPONDIO)), wa, v -> {
            wa = v;
            pageIndex = 0;
            renderTable();
        });
        waBox.setAccessibleText("Filtrar por WhatsApp");
        GridPane row1 = new GridPane();
        row1.setHgap(Ui.px(12));
        double[] percents = {35.6, 21.0, 20.4, 23.0};
        for (double pc : percents) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(pc);
            c.setFillWidth(true);
            row1.getColumnConstraints().add(c);
        }
        int col = 0;
        for (Region r : List.of(searchBox, proc, citaBox, waBox)) {
            r.setMinWidth(0);
            r.setMaxWidth(Double.MAX_VALUE);
            row1.add(r, col++, 0);
        }

        fromField = new DateField(from);
        toField = new DateField(to);
        fromField.valueProperty().addListener((o, a, b) -> {
            from = b;
            datesChanged();
        });
        toField.valueProperty().addListener((o, a, b) -> {
            to = b;
            datesChanged();
        });
        VBox fromBox = dateBox("Registrado desde", fromField);
        VBox toBox = dateBox("Registrado hasta", toField);

        rangeSeg = new Seg<>(List.of(Range.HOY, Range.SEMANA, Range.MES),
                r -> switch (r) {
                    case HOY -> "Hoy";
                    case SEMANA -> "Semana";
                    case MES -> "Mes";
                }, currentRange(), this::applyRange);

        Button clear = Ui.link("Limpiar filtros");
        clear.setMinHeight(Ui.px(46));
        clear.setOnAction(e -> clearFilters());

        CheckBox inactiveBox = new CheckBox("Ver inactivos");
        inactiveBox.setSelected(inactive);
        inactiveBox.setMinHeight(Ui.px(46));
        inactiveBox.selectedProperty().addListener((o, a, b) -> {
            inactive = b;
            pageIndex = 0;
            renderTable();
        });

        HBox.setMargin(inactiveBox, new javafx.geometry.Insets(0, 0, 0, Ui.px(4)));
        HBox row2 = new HBox(Ui.px(12), fromBox, toBox, rangeSeg, clear, inactiveBox);
        row2.setAlignment(Pos.BOTTOM_LEFT);
        row2.getStyleClass().add("sep-line");
        row2.setStyle("-fx-padding: " + Ui.px(14) + "px 0 0 0;");

        VBox card = Ui.card();
        card.setSpacing(Ui.px(14));
        card.setStyle("-fx-padding: " + Ui.px(16) + "px;");
        card.getChildren().addAll(row1, row2);
        return card;
    }

    private static VBox dateBox(String label, DateField f) {
        Label l = Ui.label(label, "lbl");
        l.setLabelFor(f);
        f.setMaxWidth(Double.MAX_VALUE);
        VBox b = new VBox(Ui.px(4), l, f);
        b.setPrefWidth(Ui.px(130));
        b.setMinWidth(Ui.px(110));
        b.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }

    private Range currentRange() {
        LocalDate today = s().time().today();
        if (to == null || from == null || !to.equals(today)) {
            return null;
        }
        if (from.equals(today)) {
            return Range.HOY;
        }
        if (from.equals(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) {
            return Range.SEMANA;
        }
        if (from.equals(today.withDayOfMonth(1))) {
            return Range.MES;
        }
        return null;
    }

    private void applyRange(Range r) {
        LocalDate today = s().time().today();
        syncingDates = true;
        try {
            to = today;
            from = switch (r) {
                case HOY -> today;
                case SEMANA -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MES -> today.withDayOfMonth(1);
            };
            fromField.setValue(from);
            toField.setValue(to);
        } finally {
            syncingDates = false;
        }
        pageIndex = 0;
        renderTable();
    }

    private void clearFilters() {
        query = "";
        processId = null;
        cita = PatientFilter.CitaFilter.ALL;
        wa = PatientFilter.WhatsAppFilter.ALL;
        from = null;
        to = null;
        inactive = false;
        pageIndex = 0;
        shell().keepScroll(this::render);
    }

    private void datesChanged() {
        if (syncingDates) {
            return;
        }
        rangeSeg.select(currentRange());
        pageIndex = 0;
        renderTable();
    }

    private PatientFilter filter() {
        return new PatientFilter(query, processId, cita, wa, from, to, inactive, false);
    }

    private boolean tableReady;

    private void renderTable() {
        if (!tableReady) {
            fillTable();
            tableReady = true;
            return;
        }
        shell().keepScroll(this::fillTable);
    }

    private void fillTable() {
        LocalDate today = s().time().today();
        List<PatientOverview> rows = PatientService.filter(all, filter());
        long activeCount = all.stream().filter(o -> o.patient().active()).count();
        long inactiveCount = all.size() - activeCount;
        lead.setText(inactive
                ? Fmt.count((int) inactiveCount, "paciente inactivo", "pacientes inactivos") + " · puedes reactivarlos"
                : Fmt.count((int) activeCount, "paciente activo", "pacientes activos") + " · los más recientes primero");

        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        pageIndex = Math.min(pageIndex, pages - 1);
        List<PatientOverview> slice = rows.subList(pageIndex * PAGE_SIZE, Math.min(rows.size(), (pageIndex + 1) * PAGE_SIZE));

        tableCard.getChildren().clear();
        tableCard.getChildren().add(fitRow(true, "Paciente", "CC", "Nacimiento", "Edad", "Teléfono", "Procedimiento",
                "# citas", "# controles", "Cita", "WhatsApp", "Registro", ""));
        if (slice.isEmpty()) {
            Label empty = Ui.wrap(all.isEmpty() ? "Aún no hay pacientes. Crea el primero con “Nuevo paciente”."
                    : inactive ? "No hay pacientes inactivos con estos filtros." : "No hay pacientes con estos filtros.",
                    "muted", "fs15");
            Button clear = Ui.link("Limpiar filtros");
            clear.setOnAction(e -> clearFilters());
            HBox box = new HBox(Ui.px(16), empty);
            if (!all.isEmpty()) {
                box.getChildren().add(clear);
            }
            box.setAlignment(Pos.CENTER_LEFT);
            box.getStyleClass().add("table-foot");
            box.setStyle("-fx-padding: " + Ui.px(28) + "px " + Ui.px(20) + "px;");
            tableCard.getChildren().add(box);
        }
        for (PatientOverview o : slice) {
            tableCard.getChildren().add(row(o, today));
        }
        tableCard.getChildren().add(footer(rows.size(), slice.size(), pages, activeCount));
        hookWidths();
        fitWidths(tableCard.getWidth());
        if (tableReady) {
            co.sv.consultorio.app.ui.Motion.settle(tableCard);
        }
    }

    private boolean widthsHooked;

    private void hookWidths() {
        if (widthsHooked) {
            return;
        }
        widthsHooked = true;
        tableCard.widthProperty().addListener((o, a, b) -> fitWidths(b.doubleValue()));
    }

    private void fitWidths(double gridWidth) {
        if (gridWidth <= 1) {
            return;
        }
        double[] widths = columnWidths(gridWidth);
        for (Node n : tableCard.getChildren()) {
            if (n instanceof GridPane g && g.getStyleClass().contains("fit")) {
                applyColumnWidths(g, widths);
            }
        }
    }

    private static double[] columnWidths(double gridWidth) {
        int n = COL_ABS.length;
        double usable = Math.floor(gridWidth - Ui.px(PAD_X) - Ui.px(COL_GAP) * (n - 1));
        if (usable < Ui.px(480)) {
            usable = Ui.px(480);
        }
        double[] abs = new double[n];
        double[] nice = new double[n];
        for (int i = 0; i < n; i++) {
            abs[i] = Ui.px(COL_ABS[i]);
            nice[i] = Math.max(abs[i], Ui.px(COL_NICE[i]));
        }
        double[] w = abs.clone();
        double extra = usable - sum(abs);
        if (extra > 0) {
            int[] order = {8, 9, 0, COL_PROC, 4, 1, 2, 7, 10, 6, 3, 11};
            for (int i : order) {
                extra = grow(w, i, nice[i], extra);
            }
            if (extra > 0) {
                w[0] += extra * 0.65;
                w[COL_PROC] += extra * 0.35;
            }
        }
        double used = 0;
        for (int i = 0; i < n; i++) {
            w[i] = Math.max(1, Math.floor(w[i]));
            used += w[i];
        }
        double slack = usable - used;
        if (slack > 0) {
            w[COL_PROC] += slack;
        }
        return w;
    }

    private static double grow(double[] w, int index, double target, double extra) {
        if (extra <= 0 || w[index] >= target) {
            return extra;
        }
        double add = Math.min(extra, target - w[index]);
        w[index] += add;
        return extra - add;
    }

    private static double sum(double[] values) {
        double s = 0;
        for (double v : values) {
            s += v;
        }
        return s;
    }

    private static void applyColumnWidths(GridPane g, double[] widths) {
        var cons = g.getColumnConstraints();
        for (int i = 0; i < widths.length && i < cons.size(); i++) {
            ColumnConstraints c = cons.get(i);
            c.setPercentWidth(-1);
            c.setMinWidth(widths[i]);
            c.setPrefWidth(widths[i]);
            c.setMaxWidth(widths[i]);
            c.setHgrow(Priority.NEVER);
            c.setFillWidth(true);
        }
        for (Node node : g.getChildren()) {
            if (!node.getStyleClass().contains("chip") || !(node instanceof Region chip)) {
                continue;
            }
            Integer col = GridPane.getColumnIndex(node);
            int index = col == null ? 0 : col;
            if (index >= 0 && index < widths.length) {
                double cap = widths[index];
                chip.setMaxWidth(cap);
                chip.setMinWidth(Math.min(Math.max(1, chip.prefWidth(-1)), cap));
            }
        }
    }

    private GridPane row(PatientOverview o, LocalDate today) {
        Patient p = o.patient();
        Label name = Ui.wrap(p.fullName(), "w600");
        name.setAlignment(Pos.CENTER_LEFT);
        Ui.tip(name, p.fullName());
        VBox who = new VBox(name);
        who.setAlignment(Pos.CENTER_LEFT);
        who.setMinWidth(0);

        Node citaChip = o.appointment() == null ? Chip.sinCita()
                : Chip.conCita(citaChipText(o, today));
        citaChip.getStyleClass().add("sm");
        java.time.LocalDate avisadoEl = o.appointment() == null || o.appointment().notifiedAt() == null
                ? null : o.appointment().notifiedAt().toLocalDate();
        HBox wa = o.waStatus() == co.sv.consultorio.domain.WaStatus.AVISADO && avisadoEl != null
                ? Chip.avisado("Avisado\n" + Fmt.shortDate(avisadoEl))
                : Chip.whatsApp(o.waStatus(), avisadoEl);
        Node waChip = Chip.clickable(wa, () -> WhatsAppFlow.forPatient(ctx, o), o.waStatus().label());
        waChip.getStyleClass().add("sm");
        Button more = Ui.ghost(Icons.DOTS, "Más acciones", 20);
        more.setOnAction(e -> openMenu(more, o));

        Integer age = p.age(today);
        String procName = o.process() == null ? "—" : o.process().name();
        Label proc = Ui.wrap(procName);
        if (o.process() != null) {
            Ui.tip(proc, procName);
        }
        String phone = Texts.formatPhone(p.phone());
        Label phoneLabel = Ui.wrap(phone, "ink2");
        Ui.tip(phoneLabel, phone);
        GridPane g = fitRow(false, who,
                cell(p.document()),
                cell(p.birthDate() == null ? "—" : p.birthDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                Ui.label(age == null ? "—" : String.valueOf(age), "ink2"),
                phoneLabel,
                proc,
                Ui.label(String.valueOf(o.citaCount()), "w600"),
                Ui.label(String.valueOf(o.controlCount()), "w600"),
                citaChip, waChip,
                cell(Fmt.shortDate(p.registeredAt().toLocalDate(), today)),
                more);
        g.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !isInsideButton(e.getTarget(), g)) {
                shell().goPaciente(p.id());
            }
        });
        if (!p.active()) {
            g.setOpacity(0.75);
        }
        return g;
    }

    /** Texto de celda que puede ocupar más de una línea sin recortarse. */
    private static Label cell(String text) {
        Label l = Ui.wrap(text == null ? "" : text, "ink2");
        if (text != null && !text.isBlank()) {
            Ui.tip(l, text);
        }
        return l;
    }

    private static boolean isInsideButton(Object target, Node stop) {
        Node n = target instanceof Node node ? node : null;
        while (n != null && n != stop) {
            if (n instanceof Button) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }

    /** La hora va en una segunda línea para que la cápsula no se corte. */
    private static String citaChipText(PatientOverview o, LocalDate today) {
        var start = o.appointment().startsAt();
        String kind = o.appointment().type() == co.sv.consultorio.domain.AppointmentType.CONTROL ? "Control" : "Cita";
        String day = start.toLocalDate().equals(today)
                ? "Hoy"
                : Fmt.dowShort(start.toLocalDate()) + " " + start.getDayOfMonth();
        return kind + " · " + day + "\n" + Fmt.time12(start);
    }

    private GridPane fitRow(boolean header, Object... cells) {
        GridPane g = new GridPane();
        g.getStyleClass().addAll("tr", "fit");
        if (header) {
            g.getStyleClass().add("th");
        }
        g.setMinWidth(0);
        g.maxWidthProperty().bind(tableCard.widthProperty());
        g.prefWidthProperty().bind(tableCard.widthProperty());
        double[] widths = columnWidths(tableCard.getWidth() > 1 ? tableCard.getWidth() : Ui.px(944));
        for (double width : widths) {
            ColumnConstraints c = new ColumnConstraints();
            c.setMinWidth(width);
            c.setPrefWidth(width);
            c.setMaxWidth(width);
            c.setFillWidth(true);
            c.setHgrow(Priority.NEVER);
            c.setHalignment(HPos.LEFT);
            g.getColumnConstraints().add(c);
        }
        for (int i = 0; i < cells.length; i++) {
            Node node;
            if (header) {
                String title = cells[i] == null ? "" : String.valueOf(cells[i]);
                Label l = Ui.label(title.isEmpty() ? "" : Ui.spacedLight(title), "th-label");
                l.setWrapText(false);
                l.setMinWidth(Region.USE_PREF_SIZE);
                node = l;
            } else {
                node = (Node) cells[i];
                if (node.getStyleClass().contains("chip")) {
                    if (node instanceof Region chip && i < widths.length) {
                        double cap = widths[i];
                        chip.setMaxWidth(cap);
                        chip.setMinWidth(Math.min(Math.max(1, chip.prefWidth(-1)), cap));
                    }
                } else {
                    loosen(node);
                }
            }
            if (node != null) {
                GridPane.setValignment(node, VPos.CENTER);
                if (header && i == 8 && node instanceof Label titleLabel) {
                    titleLabel.setMaxWidth(Double.MAX_VALUE);
                    titleLabel.setAlignment(Pos.CENTER);
                    GridPane.setHalignment(titleLabel, HPos.CENTER);
                    GridPane.setFillWidth(titleLabel, true);
                }
                if (node.getStyleClass().contains("chip")) {
                    GridPane.setHgrow(node, Priority.NEVER);
                    GridPane.setFillWidth(node, false);
                    GridPane.setHalignment(node, HPos.LEFT);
                } else {
                    GridPane.setHgrow(node, Priority.ALWAYS);
                }
                g.add(node, i, 0);
            }
        }
        return g;
    }

    private static void loosen(Node node) {
        if (node instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (child instanceof Region region) {
                    region.setMinWidth(0);
                }
            }
        }
    }

    private void openMenu(Button anchor, PatientOverview o) {
        Patient p = o.patient();
        PopMenu m = new PopMenu(shell().overlay());
        if (!p.active()) {
            m.item(Icons.CHECK_CIRCLE, "Reactivar paciente", "main1", () -> {
                s().patients().reactivate(p.id());
                shell().toast(p.fullName() + " vuelve a estar activo.");
                shell().changed();
            });
            m.item(Icons.EDIT, "Editar paciente", "", () -> shell().goPaciente(p.id()));
        } else {
            m.item(Icons.CALENDAR, "Ver citas", "main1", () -> PatientCitasDialog.show(ctx, p.id()));
            m.item(Icons.CALENDAR_PLUS, o.hasAppointment() ? "Reprogramar cita" : "Asignar cita", "",
                    () -> shell().goCitas(p.id()));
            switch (o.waStatus()) {
                case AVISADO -> {
                    m.item(Icons.CHECK, "Respondió: agendado", "main1", () -> markOutcome(o, true));
                    m.item(Icons.CHAT, "No respondió", "", () -> markOutcome(o, false));
                }
                case AGENDADO -> m.item(Icons.CHECK, "Agendado: ya respondió", "dis",
                        () -> WhatsAppFlow.forAppointment(ctx, o.appointment().id()));
                default -> m.item(Icons.CHAT, "Enviar WhatsApp", "", () -> WhatsAppFlow.forPatient(ctx, o));
            }
            m.item(Icons.EDIT, "Editar paciente", "", () -> shell().goPaciente(p.id()));
            m.separator();
            m.item(Icons.BAN, "Desactivar paciente", "", () -> ConfirmDialog.show(ctx,
                    "¿Desactivar a " + p.fullName() + "?",
                    "Dejará de aparecer en la lista, en los avisos y al asignar citas. Su historial se conserva y "
                            + "puedes reactivarlo cuando quieras desde “Ver inactivos”.",
                    "Desactivar paciente", () -> {
                        s().patients().deactivate(p.id());
                        shell().toast(p.fullName() + " quedó inactivo.");
                        shell().changed();
                    }));
        }
        anchor.getStyleClass().add("on");
        m.show(anchor, () -> anchor.getStyleClass().remove("on"));
    }

    private void markOutcome(PatientOverview o, boolean scheduled) {
        try {
            if (scheduled) {
                s().notifications().markScheduled(o.appointment().id());
                shell().toast(o.patient().fullName() + " quedó agendado: respondió por WhatsApp.");
            } else {
                s().notifications().markNoReply(o.appointment().id());
                shell().toast(o.patient().fullName() + " no respondió. Puedes enviarle otro mensaje.");
            }
            shell().changed();
        } catch (co.sv.consultorio.domain.BusinessException ex) {
            shell().error(ex.getMessage());
        }
    }

    private Node footer(int total, int shown, int pages, long activeCount) {
        String what = inactive ? "pacientes inactivos" : (total == activeCount ? "pacientes activos" : "pacientes");
        Label info = Ui.label("Mostrando " + shown + " de " + total + " " + what, "muted", "fs14");
        info.setMinWidth(0);
        info.setWrapText(true);
        HBox pager = new HBox(Ui.px(6));
        pager.setAlignment(Pos.CENTER_RIGHT);
        pager.setMinWidth(Region.USE_PREF_SIZE);
        Button prev = pg("‹", pageIndex > 0, () -> pageIndex--);
        prev.setAccessibleText("Página anterior");
        pager.getChildren().add(prev);
        int start = Math.max(0, Math.min(pageIndex - 2, pages - 5));
        int end = Math.min(pages, start + 5);
        for (int i = start; i < end; i++) {
            int idx = i;
            Button b = pg(String.valueOf(i + 1), true, () -> pageIndex = idx);
            if (i == pageIndex) {
                b.getStyleClass().add("on");
            }
            pager.getChildren().add(b);
        }
        Button next = pg("›", pageIndex < pages - 1, () -> pageIndex++);
        next.setAccessibleText("Página siguiente");
        pager.getChildren().add(next);
        BorderPane foot = new BorderPane();
        foot.setLeft(info);
        foot.setRight(pager);
        BorderPane.setAlignment(info, Pos.CENTER_LEFT);
        BorderPane.setAlignment(pager, Pos.CENTER_RIGHT);
        foot.getStyleClass().add("table-foot");
        return foot;
    }

    private Button pg(String text, boolean enabled, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().setAll("button", "pg");
        b.setDisable(!enabled);
        b.setOnAction(e -> {
            action.run();
            renderTable();
        });
        return b;
    }
}
