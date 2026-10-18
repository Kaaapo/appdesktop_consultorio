package co.sv.consultorio.app.dialogs;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.ui.Chip;
import co.sv.consultorio.app.ui.Table;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.service.ImportService;
import co.sv.consultorio.util.Fmt;
import co.sv.consultorio.util.Texts;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.geometry.VPos;

/** Revisión previa de la importación: muestra cada fila con sus errores antes de guardar. */
public final class ImportReviewDialog {

    private static final Table TABLE = new Table(150, 52, 0, 118, 210, 170);

    private ImportReviewDialog() {
    }

    public static void show(AppContext ctx, ImportService.ImportPreview preview, Runnable afterImport) {
        long ok = preview.validCount();
        long bad = preview.errorCount();
        String sub = preview.fileName() + " · " + Fmt.count(preview.rows().size(), "fila", "filas") + " · "
                + Fmt.count((int) ok, "lista para importar", "listas para importar")
                + (bad > 0 ? " · " + Fmt.count((int) bad, "con errores", "con errores") : "");
        Pop p = new Pop(ctx, "Importar pacientes", "Revisa antes de guardar", sub, 30);

        VBox rows = new VBox();
        rows.getChildren().add(TABLE.header("Fila", "Paciente", "Documento", "Procedimiento", "Estado"));
        for (ImportService.ImportRow r : preview.rows()) {
            Label name = Ui.wrap(r.fullName().isBlank() ? "(sin nombre)" : r.fullName(), "w600");
            Node state;
            if (r.valid()) {
                state = Chip.conCita("Lista");
            } else {
                VBox errs = new VBox(Ui.px(2));
                errs.getStyleClass().add("err-list");
                for (String e : r.errors()) {
                    errs.getChildren().add(Ui.wrap(e, "err"));
                }
                state = errs;
            }
            GridPane g = TABLE.row(Ui.label(String.valueOf(r.rowNumber()), "muted"), name,
                    Ui.label(Texts.isBlank(r.input().document()) ? "—" : r.input().document(), "ink2"),
                    Ui.wrap(r.processName().isBlank() ? "—" : r.processName()), state);
            for (Node c : g.getChildren()) {
                GridPane.setValignment(c, VPos.TOP);
            }
            rows.getChildren().add(g);
        }
        ScrollPane sp = new ScrollPane(rows);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPrefHeight(Ui.px(360));
        sp.setMaxHeight(Ui.px(420));
        VBox tableCard = Ui.card();
        tableCard.getChildren().add(sp);

        Button cancel = Ui.btn2("Cancelar");
        cancel.setOnAction(e -> p.close());
        Button go = Ui.btn(ok == 0 ? "Nada para importar" : "Importar " + Fmt.count((int) ok, "paciente", "pacientes"));
        go.setDisable(ok == 0);
        go.getStyleClass().add("autofocus");
        go.setOnAction(e -> {
            try {
                int n = ctx.s().imports().commit(preview);
                p.close();
                ctx.shell().toast("Se importaron " + Fmt.count(n, "paciente", "pacientes") + ".");
                if (afterImport != null) {
                    afterImport.run();
                }
                ctx.shell().changed();
            } catch (BusinessException ex) {
                ctx.shell().error(ex.getMessage());
            }
        });
        String note = bad > 0 ? "Las filas con errores no se importan. Corrígelas en el Excel y vuelve a cargarlo."
                : "Los pacientes importados quedan activos y sin cita.";
        p.add(tableCard, Pop.foot(note, cancel, go));
        p.show(880);
    }
}
