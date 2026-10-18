package co.sv.consultorio.app.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.VBox;

/** Etiqueta + control + ayuda + error, como los campos de los mockups. */
public final class Field extends VBox {

    private final Node control;
    private final Label error = Ui.wrap("", "err");
    private Label hint;

    public Field(String label, Node control) {
        super(Ui.px(6));
        this.control = control;
        Label l = Ui.label(label, "lbl");
        if (control instanceof javafx.scene.control.Control c) {
            l.setLabelFor(c);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        error.setManaged(false);
        error.setVisible(false);
        getChildren().addAll(l, control, error);
        setFillWidth(true);
    }

    public Field hint(String text) {
        hint = Ui.wrap(text, "hint");
        getChildren().add(getChildren().indexOf(error), hint);
        return this;
    }

    /** Agrega un nodo debajo del control (por ejemplo un enlace o una alerta). */
    public Field below(Node n) {
        getChildren().add(getChildren().indexOf(error), n);
        return this;
    }

    public Label hintLabel() {
        return hint;
    }

    public void error(String message) {
        boolean has = message != null && !message.isBlank();
        error.setText(has ? message : "");
        error.setManaged(has);
        error.setVisible(has);
        if (control instanceof PasswordBox pb) {
            pb.setInvalid(has);
            return;
        }
        Node target = control;
        if (target instanceof javafx.scene.layout.Pane p && !p.getChildren().isEmpty()
                && !(target instanceof Labeled)) {
            target = p.getChildren().get(0);
        }
        target.getStyleClass().remove("invalid");
        if (has) {
            target.getStyleClass().add("invalid");
        }
    }

    public Node control() {
        return control;
    }
}
