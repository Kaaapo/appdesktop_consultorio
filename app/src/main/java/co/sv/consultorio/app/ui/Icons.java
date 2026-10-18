package co.sv.consultorio.app.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.Locale;
import java.util.Map;

/** Íconos de trazo de los mockups (viewBox 24×24). El color lo da el CSS (.icon / .icon-fill). */
public final class Icons {

    public static final String HOME = "M4 11l8-6.5 8 6.5V19a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19z M10 20.5v-5h4v5";
    public static final String CALENDAR = rect(3.5, 5, 17, 15, 3)
            + " M8 3v4M16 3v4M3.5 10h17M8 14h.01M12 14h.01M16 14h.01M8 17h.01M12 17h.01";
    public static final String CALENDAR_PLUS = rect(3.5, 5, 17, 15, 3) + " M8 3v4M16 3v4M3.5 10h17M12 13v5M9.5 15.5h5";
    public static final String USERS = circle(12, 8, 3.2)
            + " M5.2 20.2c.9-3.6 3.4-5.4 6.8-5.4s5.9 1.8 6.8 5.4";
    public static final String LAYERS = "M12 3.5l8.5 4.5-8.5 4.5L3.5 8z M3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5";
    public static final String BELL = "M6 16.5V11a6 6 0 1 1 12 0v5.5l1.5 2h-15zM10 21h4";
    public static final String FILE = "M6 3.5h8l4 4V20.5H6z M14 3.5v4h4M9 13h6M9 17h6";
    public static final String FILE_X = "M6 3.5h8l4 4V20.5H6z M14 3.5v4h4M9.5 12.5l5 5M14.5 12.5l-5 5";
    public static final String SUN = circle(12, 12, 4)
            + " M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4";
    public static final String MOON = "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z";
    public static final String LOGOUT = "M10 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H10M15 8l4 4-4 4M19 12H9";
    public static final String PLUS = "M12 5v14M5 12h14";
    public static final String CHAT = "M4 5.5h16v10.5H10l-4.5 3.5V16H4z";
    public static final String SEARCH = circle(11, 11, 6.5) + " M16 16l4.5 4.5";
    public static final String EYE = "M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z " + circle(12, 12, 2.4);
    public static final String EYE_OFF = EYE + " M4 4l16 16";
    public static final String CLOSE = "M6 6l12 12M18 6L6 18";
    public static final String CHECK = "M5 12.5l4.5 4.5L19 7.5";
    public static final String EDIT = "M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4";
    public static final String BAN = circle(12, 12, 8.5) + " M6 6l12 12";
    public static final String UPLOAD = "M12 16V4M7 9l5-5 5 5M4 20h16";
    public static final String DOWNLOAD = "M12 4v12M7 11l5 5 5-5M4 20h16";
    public static final String CHECK_CIRCLE = circle(12, 12, 9) + " M8 12.5l2.7 2.7L16 9.8";
    public static final String CHEVRON_LEFT = "M14.5 6l-6 6 6 6";
    public static final String CHEVRON_RIGHT = "M9.5 6l6 6-6 6";
    public static final String UNDO = "M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11";
    public static final String DOTS = circle(5, 12, 1.5) + " " + circle(12, 12, 1.5) + " " + circle(19, 12, 1.5);

    /** Controles de ventana (viewBox 10×10). */
    public static final String WIN_MIN = "M0 5h10";
    public static final String WIN_MAX = "M.5 .5h9v9h-9z";
    public static final String WIN_RESTORE = "M.5 2.5h7v7h-7z M2.5 2.5V.5h7v7h-2";
    public static final String WIN_CLOSE = "M0 0l10 10M10 0L0 10";

    private static final Map<String, Boolean> FILLED = Map.of(DOTS, true);

    private Icons() {
    }

    public static Node of(String path, double size) {
        return of(path, size, 1.7, 24);
    }

    public static Node of(String path, double size, double strokeWidth) {
        return of(path, size, strokeWidth, 24);
    }

    public static Node window(String path) {
        return of(path, 10, 1, 10);
    }

    public static Node of(String path, double size, double strokeWidth, double viewBox) {
        double px = Ui.px(size);
        SVGPath p = new SVGPath();
        p.setContent(path);
        boolean filled = FILLED.containsKey(path);
        p.getStyleClass().add(filled ? "icon-fill" : "icon");
        if (filled) {
            p.setStrokeWidth(0);
        } else {
            p.setFill(Color.TRANSPARENT);
            p.setStrokeWidth(strokeWidth);
            p.setStrokeLineCap(StrokeLineCap.ROUND);
            p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        }
        Rectangle box = new Rectangle(0, 0, viewBox, viewBox);
        box.setFill(Color.TRANSPARENT);
        box.setMouseTransparent(true);
        Group g = new Group(box, p);
        g.setScaleX(px / viewBox);
        g.setScaleY(px / viewBox);
        StackPane holder = new StackPane(g);
        holder.setMinSize(px, px);
        holder.setPrefSize(px, px);
        holder.setMaxSize(px, px);
        holder.setMouseTransparent(true);
        holder.getStyleClass().add("icon-box");
        return holder;
    }

    /** Círculo como path (para que todo el ícono sea un solo SVGPath). */
    static String circle(double cx, double cy, double r) {
        return f("M%s %sa%s %s 0 1 0 %s 0a%s %s 0 1 0 %s 0z", cx - r, cy, r, r, 2 * r, r, r, -2 * r);
    }

    static String rect(double x, double y, double w, double h, double rx) {
        return f("M%s %sh%sa%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 %s %sh%sa%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 %s %sz",
                x + rx, y, w - 2 * rx, rx, rx, rx, rx, h - 2 * rx, rx, rx, -rx, rx, -(w - 2 * rx),
                rx, rx, -rx, -rx, -(h - 2 * rx), rx, rx, rx, -rx);
    }

    private static String f(String fmt, Object... args) {
        Object[] a = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            double d = ((Number) args[i]).doubleValue();
            a[i] = d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.2f", d);
        }
        return String.format(Locale.ROOT, fmt, a);
    }
}
