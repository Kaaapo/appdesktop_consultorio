package co.sv.consultorio.app.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

/** Campo de contraseña con botón "Mostrar / Ocultar". */
public final class PasswordBox extends StackPane {

    private final PasswordField hidden = new PasswordField();
    private final TextField shown = new TextField();
    private final Button toggle = new Button("Mostrar");

    public PasswordBox() {
        hidden.getStyleClass().add("pw");
        shown.getStyleClass().add("pw");
        shown.textProperty().bindBidirectional(hidden.textProperty());
        shown.setVisible(false);
        shown.setManaged(false);
        toggle.getStyleClass().setAll("button", "pw-toggle");
        toggle.setMnemonicParsing(false);
        toggle.setGraphic(Icons.of(Icons.EYE, 18));
        toggle.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        toggle.setAccessibleText("Ver contraseña");
        Ui.tip(toggle, "Ver contraseña");
        toggle.setOnAction(e -> setShown(!shown.isVisible()));
        getChildren().addAll(hidden, shown, toggle);
        StackPane.setAlignment(toggle, Pos.CENTER_RIGHT);
        StackPane.setMargin(toggle, new Insets(0, Ui.px(6), 0, 0));
        setMaxWidth(Double.MAX_VALUE);
    }

    private void setShown(boolean show) {
        boolean focused = hidden.isFocused() || shown.isFocused();
        shown.setVisible(show);
        shown.setManaged(show);
        hidden.setVisible(!show);
        hidden.setManaged(!show);
        toggle.setGraphic(Icons.of(show ? Icons.EYE_OFF : Icons.EYE, 18));
        toggle.setAccessibleText(show ? "Ocultar contraseña" : "Ver contraseña");
        Ui.tip(toggle, show ? "Ocultar contraseña" : "Ver contraseña");
        if (focused) {
            (show ? shown : hidden).requestFocus();
            (show ? shown : hidden).end();
        }
    }

    public String getText() {
        return hidden.getText();
    }

    public void clear() {
        hidden.clear();
    }

    public TextField field() {
        return hidden;
    }

    public void setOnAction(javafx.event.EventHandler<javafx.event.ActionEvent> h) {
        hidden.setOnAction(h);
        shown.setOnAction(h);
    }

    @Override
    public void requestFocus() {
        (shown.isVisible() ? shown : hidden).requestFocus();
    }

    public void setInvalid(boolean invalid) {
        hidden.getStyleClass().remove("invalid");
        shown.getStyleClass().remove("invalid");
        if (invalid) {
            hidden.getStyleClass().add("invalid");
            shown.getStyleClass().add("invalid");
        }
    }
}
