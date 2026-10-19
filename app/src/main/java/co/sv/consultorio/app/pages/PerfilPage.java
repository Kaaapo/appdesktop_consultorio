package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.app.ui.Field;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.PasswordBox;
import co.sv.consultorio.app.ui.Seg;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.domain.AppSettings;
import co.sv.consultorio.domain.AppSettings.ThemeMode;
import co.sv.consultorio.domain.ValidationException;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

public final class PerfilPage extends Page {

    public PerfilPage(AppContext ctx) {
        super(ctx);
    }

    @Override
    public Shell.Nav nav() {
        return Shell.Nav.PERFIL;
    }

    @Override
    public void render() {
        AppSettings st = s().settings().get();
        VBox hd = new VBox(Ui.px(8), Ui.h1("Perfil"), Ui.lead("Tu cuenta y cómo se ve el programa."));

        StackPane av = new StackPane(new Label("A"));
        av.getStyleClass().add("av-lg");
        Label name = Ui.h2("Administrador");
        name.setStyle("-fx-font-size: " + Ui.px(30) + "px;");
        VBox who = new VBox(Ui.px(6), name, Ui.wrap("Único usuario del programa. No se pueden crear más usuarios.",
                "muted", "fs15"));
        HBox heroCard = new HBox(Ui.px(20), av, Ui.hgrow(who));
        heroCard.getStyleClass().addAll("card", "hero");
        heroCard.setAlignment(Pos.CENTER_LEFT);
        heroCard.setStyle("-fx-padding: " + Ui.px(26) + "px;");

        Seg<ThemeMode> theme = new Seg<>(List.of(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO),
                m -> switch (m) {
                    case LIGHT -> "Claro";
                    case DARK -> "Oscuro";
                    case AUTO -> "Automático";
                }, st.theme(), m -> {
                    s().settings().save(s().settings().get().withTheme(m));
                    shell().rebuild();
                });
        theme.getStyleClass().add("lg");
        VBox appearance = section(16, "Apariencia", theme,
                Ui.wrap("“Automático” sigue el modo de Windows.", "muted", "fs14"));

        Button logout = Ui.btn2("Cerrar sesión", Icons.LOGOUT);
        logout.setOnAction(e -> shell().logout());
        VBox session = section(14, "Sesión",
                Ui.wrap("Al cerrar sesión, el programa vuelve a pedir la contraseña.", "muted", "fs15"), logout);

        VBox left = new VBox(Ui.px(22), heroCard, appearance, copies(), session);
        left.setPrefWidth(Ui.px(360));
        HBox.setHgrow(left, Priority.ALWAYS);

        Node pw = passwordCard();
        HBox.setHgrow(pw, Priority.ALWAYS);
        HBox body = new HBox(Ui.px(22), left, pw);
        body.setAlignment(Pos.TOP_LEFT);
        body.setFillHeight(false);
        main.getChildren().setAll(hd, body);
    }

    private VBox section(double gap, String title, Node... content) {
        VBox c = Ui.card();
        c.setSpacing(Ui.px(gap));
        c.setStyle("-fx-padding: " + Ui.px(24) + "px;");
        c.getChildren().add(Ui.h2(title));
        c.getChildren().addAll(content);
        for (Node n : content) {
            if (n instanceof Region r && !(n instanceof Label)) {
                r.setMaxWidth(Region.USE_PREF_SIZE);
            }
        }
        return c;
    }

    private Node copies() {
        Button save = Ui.btn2("Guardar copia");
        save.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar copia de los pacientes");
            fc.setInitialFileName("consultorio-copia-" + s().time().today() + ".db");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos", "*.db"));
            java.io.File file = fc.showSaveDialog(ctx.stage());
            if (file == null) {
                return;
            }
            try {
                s().db().copyTo(file.toPath());
                shell().toast("Copia guardada. Llévala en una memoria distinta a este computador.");
            } catch (java.io.IOException ex) {
                shell().error("No se pudo guardar la copia.");
            }
        });
        return section(14, "Copia de los datos",
                Ui.wrap("Las copias diarias quedan en este computador. Guarda otra en una memoria USB por si el disco falla.",
                        "muted", "fs15"),
                save);
    }

    private Node passwordCard() {
        PasswordBox current = new PasswordBox();
        PasswordBox pw = new PasswordBox();
        PasswordBox repeat = new PasswordBox();
        Field fCurrent = new Field("Contraseña actual", current);
        Field fPw = new Field("Contraseña nueva", pw);
        Field fRepeat = new Field("Repite la contraseña nueva", repeat);
        Button save = Ui.btn("Guardar contraseña");
        save.setOnAction(e -> {
            fCurrent.error(null);
            fPw.error(null);
            fRepeat.error(null);
            try {
                s().auth().changePassword(current.getText(), pw.getText(), repeat.getText());
                current.clear();
                pw.clear();
                repeat.clear();
                shell().toast("Contraseña actualizada. Úsala la próxima vez que ingreses.");
            } catch (ValidationException ex) {
                Map<String, String> m = ex.fieldErrors();
                fCurrent.error(m.get("current"));
                fPw.error(m.get("password"));
                fRepeat.error(m.get("repeat"));
            }
        });
        repeat.setOnAction(e -> save.fire());
        VBox c = Ui.card();
        c.setSpacing(Ui.px(14));
        c.setStyle("-fx-padding: " + Ui.px(20) + "px;");
        c.setPrefWidth(Ui.px(360));
        c.getChildren().addAll(Ui.h2("Cambiar contraseña"), fCurrent, fPw, fRepeat, save);
        return c;
    }
}
