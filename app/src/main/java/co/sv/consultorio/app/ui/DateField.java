package co.sv.consultorio.app.ui;

import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Selector de fecha en formato dd/mm/aaaa que acepta lo escrito al salir del campo. */
public final class DateField extends DatePicker {

    private static final DateTimeFormatter[] PARSE = {
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE
    };
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    public DateField(LocalDate value) {
        super(value);
        setPromptText("dd/mm/aaaa");
        setShowWeekNumbers(false);
        setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate d) {
                return d == null ? "" : SHOW.format(d);
            }

            @Override
            public LocalDate fromString(String s) {
                return parse(s);
            }
        });
        getEditor().focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                commitText();
            }
        });
    }

    /** El consultorio no abre el domingo: esas fechas no se pueden elegir. */
    public DateField blockSundays() {
        setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (!empty && date != null && date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    setDisable(true);
                }
            }
        });
        return this;
    }

    public void commitText() {
        String text = getEditor().getText();
        if (text == null || text.isBlank()) {
            setValue(null);
            return;
        }
        LocalDate d = parse(text);
        if (d != null) {
            setValue(d);
        } else {
            getEditor().setText(getConverter().toString(getValue()));
        }
    }

    static LocalDate parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        for (DateTimeFormatter f : PARSE) {
            try {
                return LocalDate.parse(t, f);
            } catch (DateTimeParseException ignored) {
                // siguiente formato
            }
        }
        return null;
    }
}
