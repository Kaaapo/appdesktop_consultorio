package co.sv.consultorio.app.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Menú ⋯ de una fila. */
public final class PopMenu {

    private final VBox box = new VBox();
    private final Overlay overlay;

    public PopMenu(Overlay overlay) {
        this.overlay = overlay;
        box.getStyleClass().add("menu-pop");
        box.setFillWidth(true);
        box.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP) {
                var items = box.getChildren().stream().filter(n -> n instanceof Button).toList();
                int i = items.indexOf(box.getScene().getFocusOwner());
                int next = e.getCode() == KeyCode.DOWN ? Math.min(items.size() - 1, i + 1) : Math.max(0, i - 1);
                if (!items.isEmpty()) {
                    items.get(next).requestFocus();
                }
                e.consume();
            }
        });
    }

    /**
     * @param style "" normal, "main1" principal, "dis" atenuado
     */
    public PopMenu item(String icon, String text, String style, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().setAll("button", "mi");
        if (style != null && !style.isEmpty()) {
            b.getStyleClass().add(style);
        }
        b.setGraphic(Icons.of(icon, 20));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMnemonicParsing(false);
        b.setOnAction(e -> {
            overlay.closeMenu();
            action.run();
        });
        box.getChildren().add(b);
        return this;
    }

    public PopMenu separator() {
        Region r = Ui.cls(new Region(), "menu-sep");
        VBox.setMargin(r, new javafx.geometry.Insets(Ui.px(4), Ui.px(6), Ui.px(4), Ui.px(6)));
        box.getChildren().add(r);
        return this;
    }

    public void show(Node anchor, Runnable onClose) {
        overlay.menu(anchor, box, onClose);
    }
}
