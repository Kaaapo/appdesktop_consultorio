package co.sv.consultorio.app.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Locale;

/** Atajos para construir la UI con las clases CSS de los mockups. */
public final class Ui {

    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static double scale = 1.0;

    private Ui() {
    }

    static void setScale(double s) {
        scale = s;
    }

    /** Medida en px escalada por el tamaño de letra. */
    public static double px(double v) {
        return v * scale;
    }

    /** Caja del mensaje que se pega en WhatsApp. */
    public static TextArea messageArea(boolean editable) {
        TextArea area = new TextArea();
        area.setWrapText(true);
        area.setEditable(editable);
        area.setPrefRowCount(editable ? 5 : 3);
        area.setMinHeight(px(editable ? 120 : 72));
        area.setPrefHeight(px(editable ? 150 : 96));
        return area;
    }

    public static <T extends Node> T cls(T node, String... classes) {
        node.getStyleClass().addAll(classes);
        return node;
    }

    public static Label label(String text, String... classes) {
        return cls(new Label(text), classes);
    }

    /**
     * Etiqueta que baja de renglón en los espacios si el padre la estrecha.
     * No parte palabras: eso dejaba «Octubre» y los avisos en una columna de letras.
     */
    public static Label wrap(String text, String... classes) {
        Label l = label(keepTimes(text), classes);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    /**
     * Una sola línea. Si el texto no cabe, se corta con puntos suspensivos y el cursor muestra el texto completo.
     * El ancho lo pone el padre: esta etiqueta no empuja a lo que tiene al lado.
     */
    public static Label oneLine(String text, String... styles) {
        Label l = label(text, styles);
        l.setWrapText(false);
        l.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        l.setMinWidth(0);
        l.setPrefWidth(0);
        l.setMaxWidth(Double.MAX_VALUE);
        tip(l, text == null ? "" : text);
        return l;
    }

    /** "9:00 a. m." no se reparte entre dos renglones. */
    public static String keepTimes(String text) {
        return text == null ? null : text.replace(" a. m.", "\u00A0a.\u00A0m.").replace(" p. m.", "\u00A0p.\u00A0m.");
    }

    /**
     * El texto puede ocupar varias líneas, pero no se sale de su caja.
     * Una palabra larguísima, sin espacios, se corta dentro del ancho que le toca.
     */
    public static Label stayInside(Label l) {
        l.setMinWidth(0);
        l.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        l.layoutBoundsProperty().addListener((o, a, b) -> {
            clip.setWidth(Math.max(0, b.getWidth()));
            clip.setHeight(Math.max(0, b.getHeight()));
        });
        l.setClip(clip);
        return l;
    }

    /** Una etiqueta que baja de renglón nunca queda más angosta que su palabra más larga; si no, JavaFX la parte. */
    public static Label keepWords(Label l) {
        Runnable fit = () -> {
            Text probe = new Text();
            probe.setFont(l.getFont());
            double widest = 0;
            for (String w : (l.getText() == null ? "" : l.getText()).split("\\s+")) {
                probe.setText(w);
                widest = Math.max(widest, probe.getLayoutBounds().getWidth());
            }
            l.setMinWidth(Math.ceil(widest) + l.snappedLeftInset() + l.snappedRightInset());
        };
        l.fontProperty().addListener(o -> fit.run());
        l.textProperty().addListener(o -> fit.run());
        fit.run();
        return l;
    }

    public static Label h1(String text) {
        return label(text, "h1");
    }

    public static Label h2(String text, String... extra) {
        Label l = wrap(text, "h2");
        l.getStyleClass().addAll(extra);
        return l;
    }

    public static Label lead(String text) {
        return wrap(text, "lead");
    }

    /** Antetítulo en mayúsculas con espaciado entre letras. */
    public static Label eyebrow(String text) {
        return label(spaced(text), "eyebrow");
    }

    /**
     * JavaFX no tiene letter-spacing: se emula con espacios finos (U+2009) entre letras.
     * Devuelve el texto en mayúsculas.
     */
    public static String spaced(String text) {
        String up = text.toUpperCase(ES);
        StringBuilder sb = new StringBuilder(up.length() * 2);
        for (int i = 0; i < up.length(); i++) {
            char c = up.charAt(i);
            sb.append(c);
            if (i < up.length() - 1) {
                sb.append(c == ' ' ? "\u2009\u2009" : "\u2009");
            }
        }
        return sb.toString();
    }

    /** Encabezados de tabla: mayúsculas con espaciado leve (sin espacios extra). */
    public static String spacedLight(String text) {
        return text.toUpperCase(ES);
    }

    public static Button button(String styleClass, String text, String icon) {
        Button b = new Button(text);
        b.getStyleClass().setAll("button", styleClass);
        if (icon != null) {
            b.setGraphic(Icons.of(icon, 18));
        }
        b.setMnemonicParsing(false);
        return b;
    }

    public static Button btn(String text) {
        return button("btn", text, null);
    }

    public static Button btn(String text, String icon) {
        return button("btn", text, icon);
    }

    public static Button btn2(String text) {
        return button("btn2", text, null);
    }

    public static Button btn2(String text, String icon) {
        return button("btn2", text, icon);
    }

    public static Button wa(Runnable action) {
        Button b = button("wa", "WhatsApp", Icons.CHAT);
        b.setOnAction(e -> action.run());
        return b;
    }

    /** Botón redondo solo con ícono. */
    public static Button ghost(String icon, String tooltip, double iconSize) {
        Button b = new Button();
        b.getStyleClass().setAll("button", "gh");
        b.setGraphic(Icons.of(icon, iconSize));
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        if (tooltip != null) {
            tip(b, tooltip);
            b.setAccessibleText(tooltip);
        }
        return b;
    }

    public static Button link(String text) {
        Button b = new Button(text);
        b.getStyleClass().setAll("button", "linkbtn");
        b.setMnemonicParsing(false);
        return b;
    }

    public static void tip(Node n, String text) {
        Tooltip t = new Tooltip(text);
        t.setShowDelay(Duration.millis(350));
        Tooltip.install(n, t);
    }

    public static VBox vbox(double spacing, Node... children) {
        VBox v = new VBox(px(spacing), children);
        v.setFillWidth(true);
        return v;
    }

    public static HBox hbox(double spacing, Node... children) {
        HBox h = new HBox(px(spacing), children);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    public static Region hspacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        r.setMinWidth(0);
        return r;
    }

    public static Region vspacer() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static <T extends Node> T hgrow(T n) {
        HBox.setHgrow(n, Priority.ALWAYS);
        if (n instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
            r.setMinWidth(0);
        }
        return n;
    }

    public static <T extends Node> T vgrow(T n) {
        VBox.setVgrow(n, Priority.ALWAYS);
        return n;
    }

    public static VBox card(String... extra) {
        VBox v = new VBox();
        v.getStyleClass().add("card");
        v.getStyleClass().addAll(extra);
        return v;
    }

    public static Region dot(String colorClass) {
        return cls(new Region(), "dot", colorClass);
    }

    public static Region sepLine() {
        Region r = cls(new Region(), "sep-line");
        r.setMinHeight(1);
        r.setMaxWidth(Double.MAX_VALUE);
        return r;
    }

    /** Hace clicable (ratón y teclado) un contenedor que no es botón. */
    public static <T extends Node> T clickable(T n, Runnable action) {
        n.setFocusTraversable(true);
        n.setCursor(javafx.scene.Cursor.HAND);
        n.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.getClickCount() == 1) {
                action.run();
            }
        });
        n.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                action.run();
                e.consume();
            }
        });
        return n;
    }

    /** Campo de búsqueda con lupa a la izquierda. */
    public static StackPane withLeadingIcon(Node field, String icon) {
        Node ic = Icons.of(icon, 18, 1.8);
        StackPane sp = new StackPane(field, ic);
        StackPane.setAlignment(ic, Pos.CENTER_LEFT);
        StackPane.setMargin(ic, new javafx.geometry.Insets(0, 0, 0, px(14)));
        return sp;
    }
}
