package co.sv.consultorio.app.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * Fila tipo {@code display:flex} de CSS: cada hijo tiene una base y un factor de crecimiento,
 * y el espacio sobrante (o faltante) se reparte en proporción. Los hijos se alinean arriba.
 */
public final class FlexRow extends Pane {

    private static final String BASIS = "flex-basis";
    private static final String GROW = "flex-grow";

    private final double gap;
    private boolean fillHeight;

    public FlexRow(double gap) {
        this.gap = gap;
    }

    public FlexRow add(Node n, double basis, double grow) {
        n.getProperties().put(BASIS, basis);
        n.getProperties().put(GROW, grow);
        getChildren().add(n);
        return this;
    }

    public FlexRow fillHeight() {
        this.fillHeight = true;
        return this;
    }

    private static double basis(Node n) {
        Object v = n.getProperties().get(BASIS);
        return v instanceof Double d ? d : n.prefWidth(-1);
    }

    private static double grow(Node n) {
        Object v = n.getProperties().get(GROW);
        return v instanceof Double d ? d : 0;
    }

    private double[] widths(double total) {
        var kids = getManagedChildren();
        int n = kids.size();
        double[] w = new double[n];
        double sumBasis = 0;
        double sumGrow = 0;
        for (int i = 0; i < n; i++) {
            w[i] = basis(kids.get(i));
            sumBasis += w[i];
            sumGrow += grow(kids.get(i));
        }
        double free = total - sumBasis - gap * Math.max(0, n - 1);
        for (int i = 0; i < n; i++) {
            Node k = kids.get(i);
            if (free > 0 && sumGrow > 0) {
                w[i] += free * grow(k) / sumGrow;
            } else if (free < 0 && sumBasis > 0) {
                w[i] += free * w[i] / sumBasis;
            }
            w[i] = Math.max(w[i], k.minWidth(-1));
        }
        return w;
    }

    @Override
    protected double computePrefWidth(double height) {
        Insets in = getInsets();
        double sum = 0;
        var kids = getManagedChildren();
        for (Node k : kids) {
            sum += basis(k);
        }
        return in.getLeft() + in.getRight() + sum + gap * Math.max(0, kids.size() - 1);
    }

    @Override
    protected double computeMinWidth(double height) {
        Insets in = getInsets();
        double sum = 0;
        var kids = getManagedChildren();
        for (Node k : kids) {
            sum += k.minWidth(-1);
        }
        return in.getLeft() + in.getRight() + sum + gap * Math.max(0, kids.size() - 1);
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets in = getInsets();
        double inner = (width < 0 ? computePrefWidth(-1) : width) - in.getLeft() - in.getRight();
        double[] w = widths(inner);
        double max = 0;
        var kids = getManagedChildren();
        for (int i = 0; i < kids.size(); i++) {
            max = Math.max(max, kids.get(i).prefHeight(w[i]));
        }
        return in.getTop() + in.getBottom() + max;
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        Insets in = getInsets();
        double inner = getWidth() - in.getLeft() - in.getRight();
        double h = getHeight() - in.getTop() - in.getBottom();
        double[] w = widths(inner);
        double x = in.getLeft();
        var kids = getManagedChildren();
        for (int i = 0; i < kids.size(); i++) {
            Node k = kids.get(i);
            double kh = fillHeight ? h : Math.min(h, k.prefHeight(w[i]));
            k.resizeRelocate(snapPositionX(x), snapPositionY(in.getTop()), snapSizeX(w[i]), snapSizeY(kh));
            x += w[i] + gap;
        }
    }
}
