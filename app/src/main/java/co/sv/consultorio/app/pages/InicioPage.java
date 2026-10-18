package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.app.dialogs.DailyPopup;
import co.sv.consultorio.app.dialogs.WhatsAppFlow;
import co.sv.consultorio.app.ui.Chip;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.service.DashboardService;
import co.sv.consultorio.util.Fmt;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

public final class InicioPage extends Page {

    private static final int MAX_NOTIFIED = 6;

    public InicioPage(AppContext ctx) {
        super(ctx);
    }

    @Override
    public Shell.Nav nav() {
        return Shell.Nav.INICIO;
    }

    @Override
    public void render() {
        LocalDate today = s().time().today();
        DashboardService.Stats stats = s().dashboard().stats();
        List<DashboardService.PendingItem> pending = s().dashboard().pending();
        List<AppointmentView> notified = s().dashboard().awaitingReply();

        main.getChildren().setAll(header(today), statCards(stats), columns(today, stats, pending, notified));
    }

    private Node header(LocalDate today) {
        LocalTime now = s().time().now().toLocalTime();
        String hello = now.getHour() < 12 ? "Buenos días" : now.getHour() < 19 ? "Buenas tardes" : "Buenas noches";
        Label h1 = Ui.h1(hello + ", Administrador");
        h1.setWrapText(true);
        VBox.setMargin(h1, new javafx.geometry.Insets(Ui.px(10), 0, Ui.px(8), 0));
        VBox left = new VBox(Ui.eyebrow(Fmt.headerDate(today)), h1, Ui.lead("Estas son las citas que tienes por atender."));

        Button avisos = Ui.btn2("Ver avisos del día", Icons.BELL);
        avisos.setOnAction(e -> DailyPopup.show(ctx));
        Button asignar = Ui.btn("Asignar cita", Icons.PLUS);
        asignar.setOnAction(e -> shell().go(Shell.Nav.CITAS));
        HBox buttons = Ui.hbox(10, avisos, asignar);
        buttons.setMinWidth(Region.USE_PREF_SIZE);

        HBox hd = new HBox(Ui.px(16), Ui.hgrow(left), buttons);
        hd.setAlignment(Pos.BOTTOM_LEFT);
        return hd;
    }

    private Node statCards(DashboardService.Stats st) {
        String times = st.today().isEmpty() ? "Sin citas hoy"
                : st.today().stream().map(v -> Fmt.time12(v.appointment().startsAt())).collect(Collectors.joining(" · "));
        LocalDate last = st.weekLastDay();
        HBox row = new HBox(Ui.px(16),
                stat(true, "Citas de hoy", st.today().size(), times, () -> shell().go(Shell.Nav.CITAS)),
                stat(false, "Esta semana", st.weekCount(), "hasta el " + Fmt.dow(last) + " " + last.getDayOfMonth(),
                        () -> shell().go(Shell.Nav.CITAS)),
                stat(false, "Este mes", st.monthCount(), "por venir en " + Fmt.month(s().time().today()),
                        () -> shell().go(Shell.Nav.CITAS)),
                stat(false, "Por avisar", st.pendingCount(), "pacientes sin aviso de WhatsApp",
                        () -> shell().go(Shell.Nav.PACIENTES)));
        row.setFillHeight(true);
        return row;
    }

    private Node stat(boolean hero, String title, int n, String sub, Runnable action) {
        VBox c = Ui.card("stat");
        if (hero) {
            c.getStyleClass().add("hero");
        }
        Label num = Ui.label(String.valueOf(n), "big-num");
        Label s = Ui.wrap(sub, "muted", "fs14");
        c.getChildren().addAll(Ui.eyebrow(title), num, s);
        c.setPrefWidth(0);
        c.setMinWidth(0);
        HBox.setHgrow(c, Priority.ALWAYS);
        c.setMaxWidth(Double.MAX_VALUE);
        c.setAccessibleText(title + ": " + n);
        return Ui.clickable(c, action);
    }

    private Node columns(LocalDate today, DashboardService.Stats st, List<DashboardService.PendingItem> pending,
                         List<AppointmentView> notified) {
        VBox left = new VBox(Ui.px(20), todayCard(today, st.today()), notifiedCard(today, notified));
        left.setPrefWidth(Ui.px(400));
        left.setMinWidth(0);
        HBox.setHgrow(left, Priority.ALWAYS);
        Node right = pendingCard(today, pending);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox row = new HBox(Ui.px(20), left, right);
        row.setAlignment(Pos.TOP_LEFT);
        row.setFillHeight(false);
        return row;
    }

    private VBox cardWithHead(String title, String sub) {
        VBox card = Ui.card();
        VBox head = new VBox(Ui.px(6), Ui.h2(title));
        head.getStyleClass().add("card-head");
        if (sub != null) {
            head.getChildren().add(Ui.wrap(sub, "muted", "fs14"));
            head.setStyle("-fx-padding: " + Ui.px(20) + "px " + Ui.px(22) + "px " + Ui.px(12) + "px " + Ui.px(22) + "px;");
        }
        card.getChildren().add(head);
        return card;
    }

    private Node todayCard(LocalDate today, List<AppointmentView> list) {
        VBox card = cardWithHead("Citas de hoy", null);
        if (list.isEmpty()) {
            card.getChildren().add(emptyRow("No tienes citas para hoy."));
        }
        for (AppointmentView v : list) {
            Label time = Ui.label(Fmt.time12(v.appointment().startsAt()), "w600");
            time.setMinWidth(Region.USE_PREF_SIZE);
            card.getChildren().add(li(time, nameBlock(v.patient().fullName(), v.processAndType()), action(today, v)));
        }
        return card;
    }

    private Node notifiedCard(LocalDate today, List<AppointmentView> list) {
        VBox card = cardWithHead("Sin respuesta", "Se les escribió por WhatsApp y todavía no contestan");
        if (list.isEmpty()) {
            card.getChildren().add(emptyRow("Nadie está esperando respuesta."));
        }
        list.stream().limit(MAX_NOTIFIED).forEach(v -> card.getChildren().add(li(
                nameBlock(v.patient().fullName(), Fmt.appointmentChip(v.appointment().startsAt(), today)),
                notifiedChip(v))));
        if (list.size() > MAX_NOTIFIED) {
            Button more = Ui.link("Ver los " + list.size() + " sin respuesta");
            more.setOnAction(e -> shell().goPacientes(co.sv.consultorio.service.PatientFilter.WhatsAppFilter.AVISADO));
            VBox foot = new VBox(more);
            foot.getStyleClass().add("card-foot");
            card.getChildren().add(foot);
        }
        return card;
    }

    private Node pendingCard(LocalDate today, List<DashboardService.PendingItem> items) {
        VBox card = cardWithHead("Por avisar por WhatsApp", "Sin mensaje, o escribieron y no contestaron");
        card.setPrefWidth(Ui.px(440));
        card.setMinWidth(0);
        if (items.isEmpty()) {
            card.getChildren().add(emptyRow("Todo al día: no hay pacientes por avisar."));
        }
        for (DashboardService.PendingItem it : items) {
            String sub;
            Node chip;
            if (it.hasAppointment()) {
                sub = it.appointment().processAndType();
                chip = Chip.conCita(Fmt.appointmentChip(it.appointment().appointment().startsAt(), today));
            } else {
                String proc = it.overview().process() == null ? "" : it.overview().process().name() + " · ";
                sub = proc + it.step().note(it.overview().nextCitaNumber());
                chip = Chip.sinCita();
            }
            Button wa = Ui.wa(() -> {
                if (it.hasAppointment()) {
                    WhatsAppFlow.forAppointment(ctx, it.appointment().appointment().id());
                } else {
                    WhatsAppFlow.forPatient(ctx, it.overview());
                }
            });
            Node name = nameBlock(it.overview().patient().fullName(), sub);
            card.getChildren().add(li(name, chip, wa));
        }
        Button all = Ui.link("Ver todos los pacientes");
        all.setOnAction(e -> shell().go(Shell.Nav.PACIENTES));
        VBox foot = new VBox(all);
        foot.getStyleClass().add("card-foot");
        card.getChildren().add(foot);
        return card;
    }

    private Node action(LocalDate today, AppointmentView v) {
        if (v.appointment().waStatus().canSend()) {
            return Ui.wa(() -> WhatsAppFlow.forAppointment(ctx, v.appointment().id()));
        }
        return notifiedChip(v);
    }

    private Node notifiedChip(AppointmentView v) {
        java.time.LocalDate when = v.appointment().notifiedAt() == null ? null : v.appointment().notifiedAt().toLocalDate();
        HBox chip = Chip.whatsApp(v.appointment().waStatus(), when);
        return Chip.clickable(chip, () -> WhatsAppFlow.forAppointment(ctx, v.appointment().id()), v.appointment().waStatus().label());
    }

    static VBox nameBlock(String name, String sub) {
        Label n = Ui.wrap(name == null ? "" : name, "w600");
        Ui.tip(n, name == null ? "" : name);
        Label s = Ui.wrap(sub, "muted", "fs14");
        VBox b = new VBox(n, s);
        b.setMinWidth(0);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private static HBox li(Node... nodes) {
        HBox h = new HBox(nodes);
        h.getStyleClass().add("li");
        for (Node n : nodes) {
            if (n instanceof Region r && !(n instanceof VBox)) {
                r.setMinWidth(Region.USE_PREF_SIZE);
            }
        }
        return h;
    }

    private static Node emptyRow(String text) {
        Label l = Ui.label(text, "muted", "fs15");
        l.setMinWidth(Region.USE_PREF_SIZE);
        HBox h = new HBox(l);
        h.getStyleClass().add("li");
        return h;
    }
}
