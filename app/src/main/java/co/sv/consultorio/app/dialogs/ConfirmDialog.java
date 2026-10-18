package co.sv.consultorio.app.dialogs;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.ui.Ui;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/** Confirmación con botón principal (por ejemplo "Desactivar paciente"). */
public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    public static void show(AppContext ctx, String title, String text, String confirm, Runnable onConfirm) {
        Pop p = new Pop(ctx, null, title, null, 26);
        p.box.getStyleClass().add("compact");
        Label body = Ui.wrap(text, "ink2", "fs16");
        body.setLineSpacing(Ui.px(3));
        Button cancel = Ui.btn2("Cancelar");
        cancel.setOnAction(e -> p.close());
        Button ok = Ui.btn(confirm);
        ok.getStyleClass().add("autofocus");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> {
            p.close();
            onConfirm.run();
        });
        p.add(body, Pop.foot(null, cancel, ok));
        p.show(520);
    }
}
