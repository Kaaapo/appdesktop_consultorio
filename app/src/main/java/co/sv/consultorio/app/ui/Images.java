package co.sv.consultorio.app.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Logo y monograma (negros); se invierten para fondos oscuros. */
public final class Images {

    private static final Map<String, Image> CACHE = new HashMap<>();

    private Images() {
    }

    public static synchronized Image get(String name, boolean inverted) {
        String key = name + (inverted ? ":inv" : "");
        return CACHE.computeIfAbsent(key, k -> {
            Image img = load(name);
            return inverted ? invert(img) : img;
        });
    }

    private static Image load(String name) {
        try (InputStream in = Images.class.getResourceAsStream("/co/sv/consultorio/app/img/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Falta la imagen " + name);
            }
            return new Image(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Invierte el color conservando la transparencia. */
    public static Image invert(Image src) {
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        WritableImage out = new WritableImage(w, h);
        PixelReader r = src.getPixelReader();
        PixelWriter wr = out.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = r.getColor(x, y);
                wr.setColor(x, y, new Color(1 - c.getRed(), 1 - c.getGreen(), 1 - c.getBlue(), c.getOpacity()));
            }
        }
        return out;
    }
}
