package co.sv.consultorio.app.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Chips de estado: sin cita / con cita / avisado / sin avisar. */
public final class Chip {

    private Chip() {
    }

    /**
     * @param kind  pend, avis, agen, nor, comp o na
     * @param check true = marca de verificación; false = punto
     */
    public static HBox of(String kind, String text, boolean check) {
        Node marker;
        if (check) {
            SVGPath p = new SVGPath();
            p.setContent("M1 3.6L3.9 6.4L9 1");
            p.setFill(Color.TRANSPARENT);
            p.setStrokeWidth(2);
            p.setStrokeLineCap(StrokeLineCap.SQUARE);
            p.setStrokeLineJoin(StrokeLineJoin.MITER);
            p.getStyleClass().add("check");
            p.setScaleX(Ui.px(1));
            p.setScaleY(Ui.px(1));
            marker = new javafx.scene.Group(p);
        } else {
            marker = Ui.cls(new Region(), "mark");
        }
        Label l = new Label(text);
        l.setMinWidth(Region.USE_PREF_SIZE);
        HBox h = new HBox(Ui.px(6), marker, l);
        h.getStyleClass().addAll("chip", kind);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        h.setMinWidth(Region.USE_PREF_SIZE);
        h.setMaxWidth(Region.USE_PREF_SIZE);
        h.setMaxHeight(Region.USE_PREF_SIZE);
        return h;
    }

    public static HBox avisado(String text) {
        return of("avis", text, true);
    }

    public static HBox sinAvisar() {
        return of("na", "Sin avisar", false);
    }

    public static HBox conCita(String text) {
        return of("agen", text, true);
    }

    public static HBox sinCita() {
        return of("pend", "Sin cita", false);
    }

    public static HBox whatsApp(co.sv.consultorio.domain.WaStatus status, java.time.LocalDate when) {
        return switch (status) {
            case NONE -> sinAvisar();
            case AVISADO -> avisado(when == null ? "Avisado" : "Avisado " + co.sv.consultorio.util.Fmt.shortDate(when));
            case AGENDADO -> of("agen", "Agendado", true);
            case NO_RESPONDIO -> of("nor", "No respondió", false);
        };
    }

    /** Hace el chip clicable (por ejemplo, para abrir "Ya se le avisó"). */
    public static HBox clickable(HBox chip, Runnable action, String tooltip) {
        chip.getStyleClass().add("clickable");
        chip.setOnMouseClicked(e -> action.run());
        chip.setFocusTraversable(true);
        chip.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER, SPACE -> action.run();
                default -> {
                }
            }
        });
        if (tooltip != null) {
            Ui.tip(chip, tooltip);
        }
        return chip;
    }
}
