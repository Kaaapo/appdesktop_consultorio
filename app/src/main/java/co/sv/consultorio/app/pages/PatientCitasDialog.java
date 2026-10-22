package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.dialogs.ConfirmDialog;
import co.sv.consultorio.app.dialogs.Pop;
import co.sv.consultorio.app.ui.Chip;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.util.Fmt;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Citas de un paciente: verlas, cancelarlas, reasignarlas y la fecha sugerida. */
public final class PatientCitasDialog {

    private PatientCitasDialog() {
    }

    public static void show(AppContext ctx, long patientId) {
        PatientOverview overview = ctx.s().patients().overview(patientId);
        if (overview == null) {
            ctx.shell().error("El paciente ya no existe.");
            return;
        }
        Patient patient = overview.patient();
        String sub = overview.procedures().stream()
                .map(p -> p.name() + " · cada " + p.monthsLabel())
                .collect(Collectors.joining("  ·  "));
        Pop pop = new Pop(ctx, "Paciente", patient.fullName(), sub, 28);
        VBox list = new VBox(Ui.px(8));
        Runnable reload = () -> fill(ctx, pop, patientId, list);
        reload.run();
        pop.add(list);
        pop.show(860);
    }

    private static void fill(AppContext ctx, Pop pop, long patientId, VBox list) {
        list.getChildren().clear();
        PatientOverview overview = ctx.s().patients().overview(patientId);
        if (overview == null) {
            pop.close();
            return;
        }
        LocalDate today = ctx.s().time().today();
        List<AppointmentView> citas = ctx.s().appointments().forPatient(patientId);
        LocalDate suggested = suggestedDate(ctx, overview, citas, today);
        String when = suggestText(overview, citas, today, suggested);
        Label hint = Ui.wrap(when, "fs15");
        Button use = Ui.btn2("Asignar esa fecha");
        use.setDisable(suggested == null);
        use.setOnAction(e -> {
            pop.close();
            if (suggested != null) {
                ctx.shell().goCitasOn(patientId, suggested);
            }
        });
        HBox suggest = new HBox(Ui.px(12), Ui.hgrow(hint), use);
        suggest.setAlignment(Pos.CENTER_LEFT);
        suggest.getStyleClass().add("soft-box");
        list.getChildren().add(suggest);

        if (citas.isEmpty()) {
            list.getChildren().add(Ui.wrap("Todavía no tiene citas.", "muted", "fs15"));
            return;
        }
        for (AppointmentView v : citas) {
            boolean past = v.appointment().startsAt().isBefore(ctx.s().time().now());
            String proc = v.process() == null ? "" : " · " + v.process().name();
            String title = (v.appointment().type() == AppointmentType.CITA
                    ? "Cita " + v.citaNumber()
                    : "Control") + proc;
            Label name = Ui.oneLine(title + " · " + Fmt.appointmentLong(v.appointment().startsAt()), "w600");
            name.setMinWidth(0);
            VBox text = new VBox(Ui.px(2), name);
            if (past) {
                text.getChildren().add(Ui.label("Ya pasó", "muted", "fs13"));
            }
            HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);
            text.setMaxWidth(Double.MAX_VALUE);
            Node chip = Chip.whatsApp(v.appointment().waStatus(),
                    v.appointment().notifiedAt() == null ? null : v.appointment().notifiedAt().toLocalDate());
            chip.getStyleClass().add("sm");
            Button move = Ui.btn2("Reasignar");
            move.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            move.setDisable(past);
            move.setOnAction(e -> {
                pop.close();
                ctx.shell().goReassign(patientId, v.appointment().id());
            });
            Button cancel = Ui.btn2("Cancelar");
            cancel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            cancel.setOnAction(e -> ConfirmDialog.show(ctx, "¿Cancelar esta cita?",
                    title + " del " + Fmt.appointmentLong(v.appointment().startsAt()) + " se borra del calendario.",
                    "Cancelar cita", () -> {
                        try {
                            ctx.s().appointments().cancel(v.appointment().id());
                            ctx.shell().toast("Cita cancelada.");
                            ctx.shell().changed();
                            fill(ctx, pop, patientId, list);
                        } catch (BusinessException ex) {
                            ctx.shell().error(ex.getMessage());
                        }
                    }));
            HBox actions = new HBox(Ui.px(8), chip, move, cancel);
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            HBox row = new HBox(Ui.px(16), text, actions);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("li-plain");
            list.getChildren().add(row);
        }
    }

    static LocalDate suggested(AppContext ctx, PatientOverview overview, LocalDate today) {
        return ctx.s().appointments().suggestedDay(overview, today);
    }

    private static LocalDate suggestedDate(AppContext ctx, PatientOverview overview, List<AppointmentView> citas,
                                           LocalDate today) {
        if (overview.procedures().size() <= 1) {
            return suggested(ctx, overview, today);
        }
        LocalDate soonest = null;
        for (Process process : overview.procedures()) {
            LocalDate raw = nextFor(process, citas);
            if (raw == null) {
                continue;
            }
            LocalDate day = ctx.s().appointments().firstFreeDayFrom(raw.isBefore(today) ? today : raw);
            if (soonest == null || day.isBefore(soonest)) {
                soonest = day;
            }
        }
        return soonest;
    }

    private static String suggestText(PatientOverview overview, List<AppointmentView> citas, LocalDate today,
                                      LocalDate suggested) {
        if (overview.procedures().isEmpty()) {
            return "Elige un procedimiento para calcular la siguiente cita.";
        }
        if (overview.procedures().size() == 1) {
            Process process = overview.procedures().get(0);
            return suggested == null
                    ? "Elige un procedimiento para calcular la siguiente cita."
                    : "Siguiente cita sugerida: " + Fmt.longDateDow(suggested) + " · cada " + process.monthsLabel() + ".";
        }
        return overview.procedures().stream().map(process -> {
            LocalDate raw = nextFor(process, citas);
            if (raw == null) {
                return process.name() + ": todavía sin cita";
            }
            LocalDate day = raw.isBefore(today) ? today : raw;
            return process.name() + ": siguiente " + Fmt.longDateDow(day);
        }).collect(Collectors.joining("\n"));
    }

    private static LocalDate nextFor(Process process, List<AppointmentView> citas) {
        return citas.stream()
                .filter(v -> v.appointment().type() == AppointmentType.CITA)
                .filter(v -> v.process() != null && v.process().id() == process.id())
                .map(v -> v.appointment().startsAt().toLocalDate())
                .max(Comparator.naturalOrder())
                .map(last -> last.plusMonths(process.monthsToSecond()))
                .orElse(null);
    }
}
