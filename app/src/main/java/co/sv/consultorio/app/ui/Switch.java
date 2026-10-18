package co.sv.consultorio.app.ui;

import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/** Interruptor (toggle) de la tarjeta "Asignar cita". */
public final class Switch extends ToggleButton {

    public Switch(String text) {
        super(text);
        getStyleClass().setAll("toggle-button", "tg");
        setMnemonicParsing(false);
        Region knob = Ui.cls(new Region(), "knob");
        HBox track = new HBox(knob);
        track.getStyleClass().add("track");
        setGraphic(track);
    }
}
