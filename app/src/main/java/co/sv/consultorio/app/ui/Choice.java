package co.sv.consultorio.app.ui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Lista desplegable de opciones (texto visible + valor). */
public final class Choice<T> extends ComboBox<Choice.Opt<T>> {

    public record Opt<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }

    private boolean silent;

    public Choice(List<Opt<T>> options, T selected, Consumer<T> onChange) {
        getItems().setAll(options);
        select(selected);
        setMaxWidth(Double.MAX_VALUE);
        setVisibleRowCount(10);
        Tooltip tip = new Tooltip();
        tip.setShowDelay(Duration.millis(350));
        tip.setWrapText(true);
        setTooltip(tip);
        setCellFactory(list -> wrappingCell());
        valueProperty().addListener((obs, old, now) -> {
            tip.setText(now == null ? "" : now.label());
            if (!silent && onChange != null && now != null) {
                onChange.accept(now.value());
            }
        });
        if (getValue() != null) {
            tip.setText(getValue().label());
        }
    }

    private ListCell<Opt<T>> wrappingCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Opt<T> item, boolean empty) {
                super.updateItem(item, empty);
                setWrapText(!empty && item != null);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    public static <T> Opt<T> opt(String label, T value) {
        return new Opt<>(label, value);
    }

    public T selected() {
        Opt<T> o = getValue();
        return o == null ? null : o.value();
    }

    public void select(T value) {
        silent = true;
        try {
            for (Opt<T> o : getItems()) {
                if (Objects.equals(o.value(), value)) {
                    setValue(o);
                    return;
                }
            }
            setValue(null);
        } finally {
            silent = false;
        }
    }
}
