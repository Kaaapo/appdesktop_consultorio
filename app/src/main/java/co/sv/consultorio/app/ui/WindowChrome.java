package co.sv.consultorio.app.ui;

import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;

/** Barra de título negra propia (ventana sin decoración): mover, maximizar, minimizar, cerrar y redimensionar. */
public final class WindowChrome {

    private static final double EDGE = 6;

    private final Stage stage;
    private Rectangle2D restoreBounds;
    private boolean maximized;
    private double dragDX;
    private double dragDY;
    private Button maxButton;

    public WindowChrome(Stage stage) {
        this.stage = stage;
    }

    public boolean isMaximized() {
        return maximized;
    }

    public HBox titleBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("titlebar");
        bar.getChildren().add(Ui.label(Ui.spaced("Stephany Valencia · Control de pacientes"), "titlebar-title"));
        bar.getChildren().add(Ui.hspacer());
        Button min = wc(Icons.WIN_MIN, "Minimizar");
        min.setOnAction(e -> stage.setIconified(true));
        maxButton = wc(maximized ? Icons.WIN_RESTORE : Icons.WIN_MAX, maximized ? "Restaurar" : "Maximizar");
        maxButton.setOnAction(e -> toggleMaximize());
        Button close = wc(Icons.WIN_CLOSE, "Cerrar");
        close.getStyleClass().add("close");
        close.setOnAction(e -> stage.close());
        bar.getChildren().addAll(min, maxButton, close);

        bar.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragDX = stage.getX() - e.getScreenX();
                dragDY = stage.getY() - e.getScreenY();
            }
        });
        bar.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (maximized) {
                double ratio = (e.getScreenX() - stage.getX()) / stage.getWidth();
                toggleMaximize();
                stage.setX(e.getScreenX() - stage.getWidth() * ratio);
                stage.setY(e.getScreenY() - Ui.px(18));
                dragDX = stage.getX() - e.getScreenX();
                dragDY = stage.getY() - e.getScreenY();
                return;
            }
            stage.setX(e.getScreenX() + dragDX);
            stage.setY(Math.max(screenOf().getVisualBounds().getMinY(), e.getScreenY() + dragDY));
        });
        bar.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                toggleMaximize();
            }
        });
        return bar;
    }

    private Button wc(String icon, String tip) {
        Button b = new Button();
        b.getStyleClass().setAll("button", "wc");
        b.setGraphic(Icons.window(icon));
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        b.setFocusTraversable(false);
        b.setAccessibleText(tip);
        return b;
    }

    public void toggleMaximize() {
        if (maximized) {
            if (restoreBounds != null) {
                stage.setX(restoreBounds.getMinX());
                stage.setY(restoreBounds.getMinY());
                stage.setWidth(restoreBounds.getWidth());
                stage.setHeight(restoreBounds.getHeight());
            }
            maximized = false;
        } else {
            restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            Rectangle2D vb = screenOf().getVisualBounds();
            stage.setX(vb.getMinX());
            stage.setY(vb.getMinY());
            stage.setWidth(vb.getWidth());
            stage.setHeight(vb.getHeight());
            maximized = true;
        }
        if (maxButton != null) {
            maxButton.setGraphic(Icons.window(maximized ? Icons.WIN_RESTORE : Icons.WIN_MAX));
            maxButton.setAccessibleText(maximized ? "Restaurar" : "Maximizar");
        }
    }

    /** Maximiza al iniciar si la pantalla es más pequeña que la ventana. */
    public void fitToScreen() {
        Rectangle2D vb = screenOf().getVisualBounds();
        if (stage.getWidth() >= vb.getWidth() - 40 || stage.getHeight() >= vb.getHeight() - 40) {
            restoreBounds = new Rectangle2D(vb.getMinX() + 40, vb.getMinY() + 30,
                    Math.min(stage.getWidth(), vb.getWidth() - 80), Math.min(stage.getHeight(), vb.getHeight() - 60));
            maximized = false;
            toggleMaximizeFromRestore(vb);
        } else {
            stage.setX(vb.getMinX() + (vb.getWidth() - stage.getWidth()) / 2);
            stage.setY(vb.getMinY() + (vb.getHeight() - stage.getHeight()) / 2);
        }
    }

    private void toggleMaximizeFromRestore(Rectangle2D vb) {
        stage.setX(vb.getMinX());
        stage.setY(vb.getMinY());
        stage.setWidth(vb.getWidth());
        stage.setHeight(vb.getHeight());
        maximized = true;
        if (maxButton != null) {
            maxButton.setGraphic(Icons.window(Icons.WIN_RESTORE));
        }
    }

    private Screen screenOf() {
        List<Screen> screens = Screen.getScreensForRectangle(stage.getX() + 10, stage.getY() + 10, 1, 1);
        return screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
    }

    /** Bordes para redimensionar la ventana sin decoración. */
    public void installResize(Scene scene) {
        final double[] start = new double[6];
        final Cursor[] mode = {Cursor.DEFAULT};
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (maximized) {
                scene.setCursor(Cursor.DEFAULT);
                mode[0] = Cursor.DEFAULT;
                return;
            }
            Cursor c = cursorFor(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight());
            mode[0] = c;
            scene.setCursor(c == Cursor.DEFAULT ? null : c);
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (mode[0] != Cursor.DEFAULT && !maximized) {
                start[0] = e.getScreenX();
                start[1] = e.getScreenY();
                start[2] = stage.getX();
                start[3] = stage.getY();
                start[4] = stage.getWidth();
                start[5] = stage.getHeight();
                e.consume();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            Cursor c = mode[0];
            if (c == Cursor.DEFAULT || maximized) {
                return;
            }
            e.consume();
            double dx = e.getScreenX() - start[0];
            double dy = e.getScreenY() - start[1];
            double minW = stage.getMinWidth();
            double minH = stage.getMinHeight();
            boolean east = c == Cursor.E_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.SE_RESIZE;
            boolean west = c == Cursor.W_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.SW_RESIZE;
            boolean south = c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.SW_RESIZE;
            boolean north = c == Cursor.N_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.NW_RESIZE;
            if (east) {
                stage.setWidth(Math.max(minW, start[4] + dx));
            }
            if (south) {
                stage.setHeight(Math.max(minH, start[5] + dy));
            }
            if (west) {
                double w = Math.max(minW, start[4] - dx);
                stage.setX(start[2] + start[4] - w);
                stage.setWidth(w);
            }
            if (north) {
                double h = Math.max(minH, start[5] - dy);
                stage.setY(start[3] + start[5] - h);
                stage.setHeight(h);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (mode[0] != Cursor.DEFAULT) {
                mode[0] = Cursor.DEFAULT;
                scene.setCursor(null);
            }
        });
    }

    private static Cursor cursorFor(double x, double y, double w, double h) {
        boolean l = x < EDGE;
        boolean r = x > w - EDGE;
        boolean t = y < EDGE;
        boolean b = y > h - EDGE;
        if (t && l) {
            return Cursor.NW_RESIZE;
        }
        if (t && r) {
            return Cursor.NE_RESIZE;
        }
        if (b && l) {
            return Cursor.SW_RESIZE;
        }
        if (b && r) {
            return Cursor.SE_RESIZE;
        }
        if (l) {
            return Cursor.W_RESIZE;
        }
        if (r) {
            return Cursor.E_RESIZE;
        }
        if (t) {
            return Cursor.N_RESIZE;
        }
        if (b) {
            return Cursor.S_RESIZE;
        }
        return Cursor.DEFAULT;
    }
}
