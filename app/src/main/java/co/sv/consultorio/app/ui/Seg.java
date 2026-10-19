package co.sv.consultorio.app.ui;

import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Control segmentado en píldora (Claro / Oscuro / Automático, Hoy / Semana / Mes...). */
public final class Seg<T> extends HBox {

    private final ToggleGroup group = new ToggleGroup();
    private final Map<T, ToggleButton> buttons = new LinkedHashMap<>();
    private boolean silent;

    public Seg(List<T> values, Function<T, String> label, T selected, Consumer<T> onChange) {
        getStyleClass().add("seg");
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(Region.USE_PREF_SIZE);
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);
        for (T v : values) {
            ToggleButton b = new ToggleButton(label.apply(v));
            b.getStyleClass().setAll("toggle-button", "seg-btn");
            b.setMnemonicParsing(false);
            b.setToggleGroup(group);
            b.setUserData(v);
            buttons.put(v, b);
            getChildren().add(b);
        }
        if (selected != null && buttons.containsKey(selected)) {
            buttons.get(selected).setSelected(true);
        }
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                if (old != null && !silent) {
                    old.setSelected(true);
                }
                return;
            }
            if (!silent && onChange != null) {
                @SuppressWarnings("unchecked")
                T v = (T) now.getUserData();
                onChange.accept(v);
            }
        });
    }

    /** Selecciona sin disparar el evento; null deja ninguno seleccionado. */
    public void select(T value) {
        silent = true;
        try {
            if (value == null) {
                group.selectToggle(null);
            } else if (buttons.containsKey(value)) {
                buttons.get(value).setSelected(true);
            }
        } finally {
            silent = false;
        }
    }

    public void setLabel(T value, String text) {
        ToggleButton b = buttons.get(value);
        if (b != null) {
            b.setText(text);
        }
    }
}
