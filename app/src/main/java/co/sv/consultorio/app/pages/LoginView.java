package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.ui.Field;
import co.sv.consultorio.app.ui.Images;
import co.sv.consultorio.app.ui.PasswordBox;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.ValidationException;
import co.sv.consultorio.service.AuthService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Map;

/** Pantalla de ingreso. La primera vez pide crear la contraseña del Administrador. */
public final class LoginView {

    private final AppContext ctx;
    private final HBox root = new HBox();

    public LoginView(AppContext ctx) {
        this.ctx = ctx;
        build();
    }

    public Node root() {
        return root;
    }

    private void build() {
        boolean setup = !ctx.s().auth().isConfigured();

        ImageView logo = new ImageView(Images.get("logo.png", true));
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        StackPane left = new StackPane(logo);
        left.getStyleClass().add("login-left");
        logo.fitWidthProperty().bind(javafx.beans.binding.Bindings.min(Ui.px(440),
                left.widthProperty().subtract(Ui.px(96))));
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setPrefWidth(10);
        left.setMinWidth(0);

        VBox form = new VBox();
        form.getStyleClass().add("login-form");
        VBox head = new VBox(Ui.eyebrow("Control de pacientes"));
        Label h1 = Ui.h1(setup ? "Crea la contraseña" : "Bienvenido");
        VBox.setMargin(h1, new javafx.geometry.Insets(Ui.px(10), 0, Ui.px(8), 0));
        head.getChildren().addAll(h1, Ui.lead(setup
                ? "Es la primera vez que se abre el programa. Elige la contraseña del Administrador."
                : "Ingresa para ver las citas de hoy."));

        TextField user = new TextField(AuthService.USERNAME);
        user.setEditable(false);
        user.setFocusTraversable(false);
        Field userField = new Field("Usuario", user);

        PasswordBox pw = new PasswordBox();
        Field pwField = new Field(setup ? "Contraseña nueva" : "Contraseña", pw);
        if (setup) {
            pwField.hint("Al menos " + AuthService.MIN_LENGTH + " caracteres.");
        }
        PasswordBox repeat = new PasswordBox();
        Field repeatField = new Field("Repite la contraseña", repeat);

        Button go = Ui.btn(setup ? "Crear contraseña e ingresar" : "Ingresar");
        go.getStyleClass().add("lg");
        go.setMaxWidth(Double.MAX_VALUE);
        go.setDefaultButton(true);

        Label note = Ui.wrap("Esta aplicación tiene un único usuario, el Administrador. Todo se guarda en este computador.",
                "muted", "fs14");
        note.setLineSpacing(Ui.px(3));

        form.getChildren().addAll(head, userField, pwField);
        if (setup) {
            form.getChildren().add(repeatField);
        }
        form.getChildren().addAll(go, note);

        Runnable submit = () -> {
            pwField.error(null);
            repeatField.error(null);
            if (setup) {
                try {
                    ctx.s().auth().setupPassword(pw.getText(), repeat.getText());
                    ctx.shell().loggedIn();
                } catch (ValidationException ex) {
                    Map<String, String> e = ex.fieldErrors();
                    pwField.error(e.get("password"));
                    repeatField.error(e.get("repeat"));
                }
            } else if (ctx.s().auth().login(pw.getText())) {
                ctx.shell().loggedIn();
            } else {
                pwField.error(pw.getText().isEmpty() ? "Escribe la contraseña." : "La contraseña no es correcta.");
                pw.clear();
                pw.requestFocus();
            }
        };
        go.setOnAction(e -> submit.run());
        pw.setOnAction(e -> {
            if (setup) {
                repeat.requestFocus();
            } else {
                submit.run();
            }
        });
        repeat.setOnAction(e -> submit.run());

        StackPane right = new StackPane(form);
        right.getStyleClass().add("login-right");
        StackPane.setAlignment(form, Pos.CENTER);
        form.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox.setHgrow(right, Priority.ALWAYS);
        right.setPrefWidth(10);
        right.setMinWidth(0);

        root.getChildren().addAll(left, right);
        root.setFillHeight(true);
        co.sv.consultorio.app.ui.Motion.rise(root);
        Platform.runLater(pw::requestFocus);
    }
}
