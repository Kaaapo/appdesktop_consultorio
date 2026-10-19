package co.sv.consultorio.app.pages;

import co.sv.consultorio.app.AppContext;
import co.sv.consultorio.app.Shell;
import co.sv.consultorio.service.Services;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

/** Pantalla del área principal. Guarda su estado en campos y se vuelve a dibujar con {@link #render()}. */
public abstract class Page {

    /**
     * Ancho mínimo del área principal: las columnas de la tabla de pacientes en su piso más los márgenes.
     * Más angosto se apretaban las columnas y las tarjetas; así la ventana se desplaza de lado.
     */
    public static final double MIN_WIDTH = 996;

    protected final AppContext ctx;
    protected final VBox main = new VBox();

    protected Page(AppContext ctx) {
        this.ctx = ctx;
        main.getStyleClass().add("main");
        main.setFillWidth(true);
        main.setMinWidth(co.sv.consultorio.app.ui.Ui.px(MIN_WIDTH));
    }

    public Node root() {
        return main;
    }

    /** Página del menú lateral que se marca como activa. */
    public abstract Shell.Nav nav();

    /** Vuelve a construir el contenido con los datos actuales. */
    public abstract void render();

    /** Se llama cuando cambian datos desde otro lugar (avisos, citas...). */
    public void refresh() {
        render();
    }

    protected Services s() {
        return ctx.s();
    }

    protected Shell shell() {
        return ctx.shell();
    }
}
