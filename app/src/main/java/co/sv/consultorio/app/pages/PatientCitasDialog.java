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
import java.util.List;

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
        Process process = overview.process();
        String sub = process == null ? "" : process.name() + " · cada " + process.monthsLabel();
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
        LocalDate suggested = suggested(ctx, overview, today);
        String when = suggested == null ? "Elige un procedimiento para calcular la siguiente cita."
                : "Siguiente cita sugerida: " + Fmt.longDateDow(suggested)
                + (overview.process() == null ? "" : " · cada " + overview.process().monthsLabel()) + ".";
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

        List<AppointmentView> citas = ctx.s().appointments().forPatient(patientId);
        if (citas.isEmpty()) {
            list.getChildren().add(Ui.wrap("Todavía no tiene citas.", "muted", "fs15"));
            return;
        }
        for (AppointmentView v : citas) {
            boolean past = v.appointment().startsAt().isBefore(ctx.s().time().now());
            String title = v.appointment().type() == AppointmentType.CITA
                    ? "Cita " + v.citaNumber()
                    : "Control";
            Label name = Ui.wrap(title + " · " + Fmt.appointmentLong(v.appointment().startsAt()), "w600");
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
}
