package co.sv.consultorio.app.dialogs;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.app.ui.Chip;
import co.sv.consultorio.app.ui.Seg;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.AppSettings;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.service.DashboardService;
import co.sv.consultorio.util.Fmt;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Aviso diario al abrir el programa: citas de hoy, de la semana y del mes. */
public final class DailyPopup {

    private enum Tab { HOY, SEMANA, MES }

    private final AppContext ctx;
    private final DashboardService.Popup data;
    private final LocalDate today;
    private final VBox list = new VBox();
    private final Label summary = Ui.wrap("", "w500", "fs15", "ink2");

    private DailyPopup(AppContext ctx) {
        this.ctx = ctx;
        this.data = ctx.s().dashboard().popup();
        this.today = ctx.s().time().today();
    }

    public static void show(AppContext ctx) {
        new DailyPopup(ctx).open();
    }

    private void open() {
        AppSettings st = ctx.s().settings().get();
        List<Tab> tabs = new ArrayList<>();
        if (st.popupToday()) {
            tabs.add(Tab.HOY);
        }
        if (st.popupWeek()) {
            tabs.add(Tab.SEMANA);
        }
        if (st.popupMonth()) {
            tabs.add(Tab.MES);
        }
        if (tabs.isEmpty()) {
            tabs = List.of(Tab.HOY, Tab.SEMANA, Tab.MES);
        }
        Pop p = new Pop(ctx, "Aviso diario · " + Fmt.longDateDow(today), "Citas que requieren tu atención", null, 30);
        Seg<Tab> seg = new Seg<>(tabs, this::tabLabel, tabs.get(0), this::fill);

        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setMinHeight(Ui.px(180));
        sp.setPrefHeight(Ui.px(300));
        sp.setMaxHeight(Ui.px(300));
        VBox.setMargin(summary, new javafx.geometry.Insets(Ui.px(-6), 0, 0, 0));

        Button cal = Ui.btn2("Ver calendario");
        cal.setOnAction(e -> {
            p.close();
            ctx.shell().go(Shell.Nav.CITAS);
        });
        Button ok = Ui.btn("Entendido");
        ok.getStyleClass().add("autofocus");
        ok.setOnAction(e -> p.close());
        p.add(seg, summary, sp, Pop.foot("Este aviso aparece cada día al abrir el programa.", cal, ok));
        fill(tabs.get(0));
        p.show(660);
    }

    private String tabLabel(Tab t) {
        return switch (t) {
            case HOY -> "Hoy · " + data.today().size();
            case SEMANA -> "Esta semana · " + data.weekCount();
            case MES -> "Este mes · " + data.monthCount();
        };
    }

    private void fill(Tab t) {
        list.getChildren().clear();
        switch (t) {
            case HOY -> {
                long notified = data.today().stream().filter(v -> !v.appointment().waStatus().canSend()).count();
                long pend = data.today().size() - notified;
                summary.setText(data.today().isEmpty() ? "Hoy no tienes citas asignadas."
                        : notified + (notified == 1 ? " ya avisado" : " ya avisados") + " por WhatsApp · "
                        + pend + " por avisar");
                for (AppointmentView v : data.today()) {
                    java.time.LocalDate when = v.appointment().notifiedAt() == null ? null
                            : v.appointment().notifiedAt().toLocalDate();
                    Node chip = Chip.whatsApp(v.appointment().waStatus(), when);
                    row(Fmt.time12(v.appointment().startsAt()), v.patient().fullName(), v.processAndType(), chip);
                }
            }
            case SEMANA -> {
                int pend = data.week().stream().mapToInt(DashboardService.DayGroup::pending).sum();
                summary.setText(data.weekCount() == 0 ? "No tienes citas el resto de esta semana."
                        : (data.weekCount() - pend) + " ya avisados · " + pend + " por avisar");
                for (DashboardService.DayGroup g : data.week()) {
                    String when = g.date().equals(today)
                            ? "Hoy"
                            : Fmt.dowShort(g.date()) + " " + Fmt.shortDate(g.date()) + " " + g.date().getYear();
                    row(when, names(g.items()), countLine(g.items().size(), g.pending()), badge(g.items().size()));
                }
            }
            case MES -> {
                int pend = data.month().stream().mapToInt(DashboardService.WeekGroup::pending).sum();
                summary.setText(data.monthCount() == 0 ? "No tienes más citas este mes."
                        : (data.monthCount() - pend) + " ya avisados · " + pend + " por avisar");
                for (DashboardService.WeekGroup w : data.month()) {
                    String when = w.from().equals(w.to()) ? Fmt.shortDate(w.from(), today)
                            : w.from().getDayOfMonth() + " – " + Fmt.shortDate(w.to(), today);
                    row(when, w.title(), countLine(w.count(), w.pending()), badge(w.count()));
                }
            }
        }
        if (list.getChildren().isEmpty()) {
            Label empty = Ui.wrap("Nada pendiente por aquí.", "muted", "fs15");
            empty.setStyle("-fx-padding: " + Ui.px(16) + "px 0;");
            list.getChildren().add(empty);
        }
    }

    private static String countLine(int count, int pending) {
        if (count == 0) {
            return "Sin citas";
        }
        String c = Fmt.count(count, "cita", "citas");
        if (pending == 0) {
            return c + " · " + (count == 1 ? "ya avisada" : "ya avisadas");
        }
        return c + " · " + pending + " por avisar";
    }

    private static String names(List<AppointmentView> items) {
        if (items.size() <= 3) {
            return String.join(", ", items.stream().map(v -> v.patient().fullName()).toList());
        }
        List<String> first = items.stream().limit(3).map(v -> v.patient().fullName()).toList();
        return String.join(", ", first) + " y " + (items.size() - 3) + " más";
    }

    private static Node badge(int n) {
        Label l = Ui.label(String.valueOf(n), "num");
        l.setMinWidth(Label.USE_PREF_SIZE);
        return l;
    }

    private void row(String when, String title, String sub, Node right) {
        GridPane g = new GridPane();
        g.getStyleClass().add("pr");
        if (list.getChildren().isEmpty()) {
            g.getStyleClass().add("first");
        }
        ColumnConstraints c0 = new ColumnConstraints(Ui.px(128));
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setMinWidth(0);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHalignment(HPos.RIGHT);
        g.getColumnConstraints().addAll(c0, c1, c2);
        Label w = Ui.oneLine(when, "w600");
        w.setMaxWidth(Ui.px(128));
        w.setPrefWidth(Ui.px(128));
        Label t = Ui.oneLine(title, "w500");
        Label s = Ui.oneLine(sub, "muted", "fs14");
        VBox mid = new VBox(t, s);
        g.add(w, 0, 0);
        g.add(mid, 1, 0);
        g.add(right, 2, 0);
        list.getChildren().add(g);
    }
}
