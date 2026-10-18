package co.sv.consultorio.app.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/** Apariciones lentas. No dejan la pantalla en caché y no mueven el scroll. */
public final class Motion {

    private static final Duration SLOW = Duration.millis(520);

    private Motion() {
    }

    /** La pantalla entra bajando un poco y apareciendo. */
    public static void rise(Node node) {
        stop(node);
        node.setOpacity(0);
        node.setTranslateY(18);
        FadeTransition fade = new FadeTransition(SLOW, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(SLOW, node);
        slide.setFromY(18);
        slide.setToY(0);
        ParallelTransition play = new ParallelTransition(fade, slide);
        play.setOnFinished(e -> {
            node.setOpacity(1);
            node.setTranslateY(0);
        });
        remember(node, play);
        play.play();
    }

    /** Un cuadro se abre con un fundido y un leve aumento. */
    public static void pop(Node node) {
        stop(node);
        node.setOpacity(0);
        node.setScaleX(0.96);
        node.setScaleY(0.96);
        FadeTransition fade = new FadeTransition(Duration.millis(420), node);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(420), node);
        scale.setToX(1);
        scale.setToY(1);
        ParallelTransition play = new ParallelTransition(fade, scale);
        play.setOnFinished(e -> {
            node.setOpacity(1);
            node.setScaleX(1);
            node.setScaleY(1);
        });
        remember(node, play);
        play.play();
    }

    /**
     * Un bloque que se acaba de redibujar aparece sin moverse, para no empujar el scroll.
     * Empieza ya visible y solo gana presencia, así no hay un parpadeo en blanco.
     */
    public static void settle(Node node) {
        if (node == null) {
            return;
        }
        stop(node);
        node.setOpacity(0.45);
        node.setTranslateY(0);
        FadeTransition fade = new FadeTransition(Duration.millis(240), node);
        fade.setFromValue(0.45);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.setOnFinished(e -> node.setOpacity(1));
        remember(node, fade);
        fade.play();
    }

    /** Cierra un cuadro, un menú o un aviso: se desvanece y se encoge un poco, y luego se quita. */
    public static void leave(Node node, Runnable done) {
        if (node == null) {
            if (done != null) {
                done.run();
            }
            return;
        }
        stop(node);
        node.setMouseTransparent(true);
        FadeTransition fade = new FadeTransition(Duration.millis(220), node);
        fade.setFromValue(node.getOpacity());
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_BOTH);
        ScaleTransition scale = new ScaleTransition(Duration.millis(220), node);
        scale.setToX(0.97);
        scale.setToY(0.97);
        scale.setInterpolator(Interpolator.EASE_BOTH);
        ParallelTransition play = new ParallelTransition(fade, scale);
        play.setOnFinished(e -> {
            node.setOpacity(0);
            if (done != null) {
                done.run();
            }
        });
        remember(node, play);
        play.play();
    }

    private static void stop(Node node) {
        Object running = node.getProperties().remove("sv-motion");
        if (running instanceof javafx.animation.Animation animation) {
            animation.stop();
        }
    }

    private static void remember(Node node, javafx.animation.Animation animation) {
        node.getProperties().put("sv-motion", animation);
    }
}
