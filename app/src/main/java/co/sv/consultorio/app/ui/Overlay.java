package co.sv.consultorio.app.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;

/** Capa encima de la ventana para diálogos (con fondo oscurecido), menús ⋯ y avisos breves. */
public final class Overlay {

    private final StackPane layer = new StackPane();
    private final StackPane toastLayer = new StackPane();
    private final Deque<Modal> modals = new ArrayDeque<>();
    private Pane menuCatcher;
    private Runnable menuOnClose;
    private final javafx.event.EventHandler<KeyEvent> keyHandler = this::onKey;

    public Overlay() {
        layer.setPickOnBounds(false);
        toastLayer.setPickOnBounds(false);
        toastLayer.setMouseTransparent(true);
        toastLayer.setAlignment(Pos.BOTTOM_CENTER);
        toastLayer.setPadding(new Insets(0, 0, Ui.px(34), 0));
        layer.sceneProperty().addListener((obs, old, scene) -> {
            if (old != null) {
                old.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            }
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            }
        });
    }

    private void onKey(KeyEvent e) {
        if (e.getCode() != KeyCode.ESCAPE) {
            return;
        }
        if (menuCatcher != null) {
            closeMenu();
            e.consume();
        } else if (!modals.isEmpty() && modals.peek().escCloses) {
            modals.peek().close();
            e.consume();
        }
    }

    public Node layer() {
        return layer;
    }

    public Node toastLayer() {
        return toastLayer;
    }

    public boolean hasModal() {
        return !modals.isEmpty();
    }

    public void closeAll() {
        closeMenu(true);
        while (!modals.isEmpty()) {
            modals.peek().discard();
        }
        layer.getChildren().clear();
    }

    /** Muestra un diálogo centrado. {@code width} en px sin escalar. */
    public Modal modal(Region content, double width) {
        closeMenu();
        Modal m = new Modal(content, width);
        modals.push(m);
        layer.getChildren().add(m.scrim);
        m.focusFirst();
        return m;
    }

    public final class Modal {
        private final StackPane scrim = new StackPane();
        private final Node previousFocus;
        private Runnable onClose;
        private boolean escCloses = true;
        private boolean closed;

        private Modal(Region content, double width) {
            previousFocus = layer.getScene() == null ? null : layer.getScene().getFocusOwner();
            scrim.getStyleClass().add("scrim");
            content.setMaxWidth(Ui.px(width));
            content.setPrefWidth(Ui.px(width));
            content.setMaxHeight(Region.USE_PREF_SIZE);
            scrim.getChildren().add(content);
            scrim.setPadding(new Insets(Ui.px(24)));
            StackPane.setAlignment(content, Pos.CENTER);
            Motion.pop(content);
            scrim.setOnMouseClicked(e -> {
                if (e.getTarget() == scrim && escCloses) {
                    close();
                }
            });
        }

        public Modal onClose(Runnable r) {
            this.onClose = r;
            return this;
        }

        public Modal escCloses(boolean v) {
            this.escCloses = v;
            return this;
        }

        public boolean isOpen() {
            return !closed;
        }

        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            modals.remove(this);
            scrim.setMouseTransparent(true);
            if (previousFocus != null && previousFocus.getScene() != null) {
                previousFocus.requestFocus();
            }
            Runnable done = onClose;
            onClose = null;
            Motion.leave(scrim, () -> layer.getChildren().remove(scrim));
            if (done != null) {
                done.run();
            }
        }

        /** Quita el cuadro al momento, sin animación. Lo usa el cambio de tema. */
        private void discard() {
            if (!closed) {
                closed = true;
                modals.remove(this);
            }
            layer.getChildren().remove(scrim);
        }

        private void focusFirst() {
            javafx.application.Platform.runLater(() -> {
                Node auto = scrim.lookup(".autofocus");
                if (auto != null) {
                    auto.requestFocus();
                } else {
                    scrim.requestFocus();
                }
            });
        }
    }

    /** Menú flotante junto a {@code anchor} (alineado a su borde derecho). */
    public void menu(Node anchor, VBox menu, Runnable onClose) {
        closeMenu();
        menuOnClose = onClose;
        menuCatcher = new Pane();
        menuCatcher.setPickOnBounds(true);
        menuCatcher.setOnMousePressed(e -> {
            if (e.getTarget() == menuCatcher) {
                closeMenu();
            }
        });
        menuCatcher.getChildren().add(menu);
        menu.setManaged(false);
        layer.getChildren().add(menuCatcher);
        menu.applyCss();
        menu.autosize();
        Bounds a = anchor.localToScene(anchor.getBoundsInLocal());
        Bounds origin = layer.localToScene(layer.getBoundsInLocal());
        double w = menu.prefWidth(-1);
        double h = menu.prefHeight(w);
        menu.resize(w, h);
        double x = a.getMaxX() - origin.getMinX() - w;
        double y = a.getMaxY() - origin.getMinY() + Ui.px(6);
        if (y + h > layer.getHeight() - Ui.px(8)) {
            y = a.getMinY() - origin.getMinY() - h - Ui.px(6);
        }
        x = Math.max(Ui.px(8), Math.min(x, layer.getWidth() - w - Ui.px(8)));
        y = Math.max(Ui.px(8), y);
        menu.relocate(x, y);
        Motion.pop(menu);
        javafx.application.Platform.runLater(() -> {
            Node first = menu.lookup(".mi");
            if (first != null) {
                first.requestFocus();
            }
        });
    }

    public void closeMenu() {
        closeMenu(false);
    }

    private void closeMenu(boolean now) {
        if (menuCatcher == null) {
            return;
        }
        Pane leaving = menuCatcher;
        Runnable done = menuOnClose;
        menuCatcher = null;
        menuOnClose = null;
        leaving.setMouseTransparent(true);
        if (now) {
            layer.getChildren().remove(leaving);
        } else {
            Motion.leave(leaving, () -> layer.getChildren().remove(leaving));
        }
        if (done != null) {
            done.run();
        }
    }

    public void toast(String message, boolean error) {
        HBox t = new HBox(Ui.px(10));
        t.getStyleClass().add("toast");
        if (error) {
            t.getStyleClass().add("error");
        } else {
            t.getChildren().add(Icons.of(Icons.CHECK_CIRCLE, 20));
        }
        Label l = new Label(message);
        l.setWrapText(true);
        l.setMaxWidth(Ui.px(620));
        t.getChildren().add(l);
        t.setMaxWidth(Region.USE_PREF_SIZE);
        t.setMaxHeight(Region.USE_PREF_SIZE);
        toastLayer.getChildren().setAll(t);
        t.setOpacity(0);
        t.setTranslateY(10);
        FadeTransition in = new FadeTransition(Duration.millis(280), t);
        in.setFromValue(0);
        in.setToValue(1);
        javafx.animation.TranslateTransition rise = new javafx.animation.TranslateTransition(Duration.millis(280), t);
        rise.setFromY(10);
        rise.setToY(0);
        javafx.animation.ParallelTransition enter = new javafx.animation.ParallelTransition(in, rise);
        PauseTransition stay = new PauseTransition(Duration.millis(error ? 4200 : 2800));
        FadeTransition out = new FadeTransition(Duration.millis(240), t);
        out.setToValue(0);
        SequentialTransition seq = new SequentialTransition(enter, stay, out);
        seq.setOnFinished(e -> toastLayer.getChildren().remove(t));
        seq.play();
    }
}
