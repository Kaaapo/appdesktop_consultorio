package co.sv.consultorio.app.ui;

import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/** Filas de tabla con columnas fijas (como el grid de los mockups). Ancho 0 = columna que crece. */
public final class Table {

    private final double[] widths;
    private final double minGrow;

    private String extraClass;

    public Table(double minGrow, double... widths) {
        this.minGrow = minGrow;
        this.widths = widths;
    }

    public Table styled(String styleClass) {
        this.extraClass = styleClass;
        return this;
    }

    public GridPane row(Node... cells) {
        GridPane g = new GridPane();
        g.getStyleClass().add("tr");
        if (extraClass != null) {
            g.getStyleClass().add(extraClass);
        }
        for (double w : widths) {
            ColumnConstraints c = new ColumnConstraints();
            if (w <= 0) {
                c.setHgrow(Priority.ALWAYS);
                c.setMinWidth(Ui.px(minGrow));
                c.setPrefWidth(Ui.px(minGrow));
            } else {
                c.setHgrow(Priority.NEVER);
                c.setMinWidth(Ui.px(w));
                c.setPrefWidth(Ui.px(w));
                c.setMaxWidth(Ui.px(w));
            }
            c.setFillWidth(true);
            g.getColumnConstraints().add(c);
        }
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null) {
                g.add(cells[i], i, 0);
            }
        }
        return g;
    }

    public GridPane header(String... titles) {
        Node[] cells = new Node[titles.length];
        for (int i = 0; i < titles.length; i++) {
            Label l = Ui.wrap(titles[i].isEmpty() ? "" : Ui.spacedLight(titles[i]), "th-label");
            cells[i] = l;
        }
        GridPane g = row(cells);
        g.getStyleClass().add("th");
        return g;
    }

    public static void alignRight(GridPane g, int column) {
        g.getColumnConstraints().get(column).setHalignment(HPos.RIGHT);
    }
}
