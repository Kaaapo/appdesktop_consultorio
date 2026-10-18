package co.sv.consultorio.app.dialogs;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.Overlay;
import co.sv.consultorio.app.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Diálogo centrado con título en serif, subtítulo opcional y botón cerrar. */
public class Pop {

    protected final AppContext ctx;
    protected final VBox box = new VBox();
    protected final VBox head = new VBox(Ui.px(8));
    protected Overlay.Modal modal;

    public Pop(AppContext ctx, String eyebrow, String title, String subtitle, double titleSize) {
        this.ctx = ctx;
        box.getStyleClass().add("pop");
        box.setSpacing(Ui.px(20));
        if (eyebrow != null) {
            head.getChildren().add(Ui.eyebrow(eyebrow));
        }
        Label t = Ui.h2(title);
        t.setStyle("-fx-font-size: " + Ui.px(titleSize) + "px;");
        head.getChildren().add(t);
        if (subtitle != null) {
            Label sub = Ui.wrap(subtitle, "muted", "fs15");
            head.getChildren().add(sub);
        }
        Button close = Ui.ghost(Icons.CLOSE, "Cerrar", 20);
        close.getStyleClass().add("ring");
        close.setOnAction(e -> close());
        HBox top = new HBox(Ui.px(16), Ui.hgrow(head), close);
        top.setAlignment(Pos.TOP_LEFT);
        box.getChildren().add(top);
    }

    public VBox head() {
        return head;
    }

    public void add(Node... nodes) {
        box.getChildren().addAll(nodes);
    }

    public Overlay.Modal show(double width) {
        modal = ctx.shell().overlay().modal(box, width);
        return modal;
    }

    public void close() {
        if (modal != null) {
            modal.close();
        }
    }

    /** Pie con texto a la izquierda y botones a la derecha, separado por una línea. */
    public static HBox foot(String note, Node... buttons) {
        HBox f = new HBox(Ui.px(12));
        f.getStyleClass().add("pop-foot");
        if (note != null) {
            Label l = Ui.wrap(note, "muted", "fs14");
            l.setMinWidth(Ui.px(160));
            l.setPrefWidth(Ui.px(270));
            l.setMaxWidth(Ui.px(270));
            f.getChildren().add(l);
        }
        f.getChildren().add(Ui.hspacer());
        HBox bs = new HBox(Ui.px(10), buttons);
        bs.setAlignment(Pos.CENTER_RIGHT);
        bs.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        f.getChildren().add(bs);
        return f;
    }
}
