package co.sv.consultorio.app;

import co.sv.consultorio.app.pages.CitasPage;
import co.sv.consultorio.app.pages.InicioPage;
import co.sv.consultorio.app.pages.LoginView;
import co.sv.consultorio.app.pages.Page;
import co.sv.consultorio.app.pages.PacienteFormPage;
import co.sv.consultorio.app.pages.PacientesPage;
import co.sv.consultorio.app.pages.PerfilPage;
import co.sv.consultorio.app.pages.ProcesosPage;
import co.sv.consultorio.app.pages.ReportesPage;
import co.sv.consultorio.app.dialogs.DailyPopup;
import co.sv.consultorio.app.ui.Icons;
import co.sv.consultorio.app.ui.Images;
import co.sv.consultorio.app.ui.Motion;
import co.sv.consultorio.app.ui.Overlay;
import co.sv.consultorio.app.ui.Theme;
import co.sv.consultorio.app.ui.Ui;
import co.sv.consultorio.app.ui.WindowChrome;
import co.sv.consultorio.domain.AppSettings;
import co.sv.consultorio.service.DashboardService;
import co.sv.consultorio.service.PatientFilter;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Marco de la ventana: barra de título, franja de estado, menú lateral, área principal y capa de diálogos. */
public final class Shell {

    public enum Nav { INICIO, CITAS, PACIENTES, PROCESOS, REPORTES, PERFIL }

    private static final double SIDE_WIDTH = 272;
    private static final double RAIL_WIDTH = 76;

    private final AppContext ctx;
    private final StackPane root = new StackPane();
    private final BorderPane frame = new BorderPane();
    private final Overlay overlay = new Overlay();
    private final WindowChrome chrome;
    private final Scene scene;
    private final ScrollPane scroll = new ScrollPane();
    private final Map<Nav, Button> navButtons = new EnumMap<>(Nav.class);
    private HBox strip;
    private Page page;
    private Supplier<Page> pageFactory;
    private boolean loggedIn;
    private boolean darkApplied;
    private boolean rail;

    Shell(AppContext ctx) {
        this.ctx = ctx;
        this.chrome = new WindowChrome(ctx.stage());
        root.getChildren().addAll(frame, overlay.layer(), overlay.toastLayer());
        scene = new Scene(root, 1280, 900, Color.BLACK);
        chrome.installResize(scene);
        scene.widthProperty().addListener((obs, old, w) -> updateRail());
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("main-scroll");
        scroll.vvalueProperty().addListener((obs, old, value) -> holdScroll(value.doubleValue()));
        scroll.addEventFilter(ScrollEvent.SCROLL, e -> beginUserScroll());
        scroll.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() instanceof ScrollBar) {
                beginUserScroll();
            }
        });
        scroll.skinProperty().addListener((obs, prev, skin) -> hookScrollBar());
        Platform.runLater(this::hookScrollBar);
        applyTheme();
        startAutoThemeWatch();
    }

    public Scene scene() {
        return scene;
    }

    public WindowChrome chrome() {
        return chrome;
    }

    public Overlay overlay() {
        return overlay;
    }

    public boolean isDark() {
        return darkApplied;
    }

    // ---------------------------------------------------------------- sesión

    public void showLogin() {
        loggedIn = false;
        overlay.closeAll();
        page = null;
        pageFactory = null;
        frame.setTop(chrome.titleBar());
        frame.setLeft(null);
        frame.setCenter(new LoginView(ctx).root());
    }

    /** Después de ingresar: Inicio y, la primera vez del día, el aviso diario. */
    public void loggedIn() {
        loggedIn = true;
        go(Nav.INICIO);
        AppSettings st = ctx.s().settings().get();
        if (st.anyPopup() && !ctx.s().time().today().equals(st.lastPopupDate())) {
            ctx.s().settings().save(st.withLastPopupDate(ctx.s().time().today()));
            Platform.runLater(() -> DailyPopup.show(ctx));
        }
    }

    public void logout() {
        showLogin();
    }

    // ---------------------------------------------------------------- navegación

    public void go(Nav nav) {
        switch (nav) {
            case INICIO -> show(() -> new InicioPage(ctx));
            case CITAS -> show(() -> new CitasPage(ctx, null));
            case PACIENTES -> show(() -> new PacientesPage(ctx, null));
            case PROCESOS -> show(() -> new ProcesosPage(ctx));
            case REPORTES -> show(() -> new ReportesPage(ctx));
            case PERFIL -> show(() -> new PerfilPage(ctx));
        }
    }

    /** Citas con el paciente ya elegido en "Asignar cita". */
    public void goCitas(Long patientId) {
        show(() -> new CitasPage(ctx, patientId));
    }

    /** Abre Citas con el paciente y el día sugerido ya elegidos. */
    public void goCitasOn(long patientId, java.time.LocalDate day) {
        show(() -> {
            CitasPage page = new CitasPage(ctx, patientId);
            page.prepareDay(day);
            return page;
        });
    }

    /** Abre Citas para mover una cita concreta del paciente. */
    public void goReassign(long patientId, long appointmentId) {
        show(() -> {
            CitasPage page = new CitasPage(ctx, patientId);
            page.prepareReassign(appointmentId);
            return page;
        });
    }

    public void goPacientes(PatientFilter.WhatsAppFilter preset) {
        show(() -> new PacientesPage(ctx, preset));
    }

    /** Formulario de paciente: null = nuevo. */
    public void goPaciente(Long patientId) {
        show(() -> new PacienteFormPage(ctx, patientId));
    }

    private void show(Supplier<Page> factory) {
        openPage(factory, null);
    }

    private void openPage(Supplier<Page> factory, Double keepScroll) {
        overlay.closeMenu();
        pageFactory = factory;
        page = factory.get();
        page.render();
        if (frame.getLeft() == null || !loggedIn || strip == null) {
            buildFrame();
        }
        scroll.setContent(page.root());
        frame.setCenter(scroll);
        if (keepScroll == null) {
            writeScroll(0);
        } else {
            savedScroll = keepScroll;
            writeScroll(keepScroll);
        }
        Motion.rise(page.root());
        markNav(page.nav());
        refreshStrip();
    }

    private int scrollDepth;
    private double savedScroll;
    private double stableScroll;
    private boolean pinning;
    private boolean userScroll;
    private boolean correcting;
    private Region pinned;
    private double pinnedMin = Region.USE_COMPUTED_SIZE;

    /** El foco de un menú o un botón no puede llevar el scroll arriba. Solo se mueve si lo mueve la persona. */
    private void holdScroll(double next) {
        if (correcting) {
            return;
        }
        if (pinning) {
            if (Math.abs(next - savedScroll) > 0.0001) {
                correcting = true;
                scroll.setVvalue(savedScroll);
                Platform.runLater(() -> correcting = false);
            }
            return;
        }
        if (userScroll) {
            stableScroll = next;
            return;
        }
        if (Math.abs(next - stableScroll) > 0.0001) {
            correcting = true;
            scroll.setVvalue(stableScroll);
            Platform.runLater(() -> correcting = false);
        }
    }

    private void beginUserScroll() {
        userScroll = true;
        Platform.runLater(() -> {
            userScroll = false;
            stableScroll = scroll.getVvalue();
        });
    }

    private void writeScroll(double v) {
        userScroll = true;
        stableScroll = v;
        savedScroll = v;
        scroll.setVvalue(v);
        userScroll = false;
    }

    private void hookScrollBar() {
        for (Node n : scroll.lookupAll(".scroll-bar")) {
            if (!(n instanceof ScrollBar bar) || bar.getOrientation() != Orientation.VERTICAL) {
                continue;
            }
            if (Boolean.TRUE.equals(bar.getProperties().get("sv-hook"))) {
                continue;
            }
            bar.getProperties().put("sv-hook", true);
            bar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> userScroll = true);
            bar.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> Platform.runLater(() -> {
                userScroll = false;
                stableScroll = scroll.getVvalue();
            }));
        }
    }

    /**
     * Ejecuta un redibujado sin llevar el scroll al inicio. Mientras dura, la página
     * no puede encogerse y el scroll se queda quieto, así no hay un salto ni un tirón.
     */
    public void keepScroll(Runnable work) {
        boolean outer = scrollDepth == 0;
        if (outer) {
            savedScroll = scroll.getVvalue();
            stableScroll = savedScroll;
            pinning = true;
            if (scroll.getContent() instanceof Region region && region.getHeight() > 1) {
                pinned = region;
                pinnedMin = region.getMinHeight();
                region.setMinHeight(region.getHeight());
            }
        }
        scrollDepth++;
        try {
            work.run();
            if (outer) {
                scroll.setVvalue(savedScroll);
            }
        } finally {
            scrollDepth--;
            if (outer) {
                double v = savedScroll;
                Platform.runLater(() -> {
                    scroll.setVvalue(v);
                    Platform.runLater(() -> {
                        if (pinned != null) {
                            pinned.setMinHeight(pinnedMin);
                            pinned = null;
                        }
                        pinning = false;
                        writeScroll(v);
                    });
                });
            }
        }
    }

    /** Hubo cambios de datos: actualiza la franja y la pantalla actual sin mover el scroll. */
    public void changed() {
        refreshStrip();
        if (page != null) {
            keepScroll(page::refresh);
        }
    }

    /** Vuelve a aplicar tema y tamaño de letra y reconstruye toda la ventana. */
    public void rebuild() {
        double v = scroll.getVvalue();
        applyTheme();
        if (!loggedIn) {
            showLogin();
            return;
        }
        overlay.closeAll();
        strip = null;
        frame.setLeft(null);
        if (pageFactory != null) {
            openPage(pageFactory, v);
        }
    }

    public void toast(String message) {
        overlay.toast(message, false);
    }

    public void error(String message) {
        overlay.toast(message, true);
    }

    // ---------------------------------------------------------------- tema

    private void applyTheme() {
        AppSettings st = ctx.s().settings().get();
        darkApplied = Theme.isDark(st.theme());
        Theme.apply(scene, darkApplied, 1);
    }

    public void toggleDark() {
        AppSettings st = ctx.s().settings().get();
        ctx.s().settings().save(st.withTheme(darkApplied ? AppSettings.ThemeMode.LIGHT : AppSettings.ThemeMode.DARK));
        rebuild();
    }

    private void startAutoThemeWatch() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tema-automatico");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> {
            if (ctx.s().settings().get().theme() != AppSettings.ThemeMode.AUTO) {
                return;
            }
            boolean dark = Theme.isDark(AppSettings.ThemeMode.AUTO);
            if (dark != darkApplied) {
                Platform.runLater(this::rebuild);
            }
        }, 8, 8, TimeUnit.SECONDS);
    }

    // ---------------------------------------------------------------- marco

    private void buildFrame() {
        VBox top = new VBox(chrome.titleBar(), buildStrip());
        frame.setTop(top);
        rail = wantsRail();
        frame.setLeft(buildSideScroll());
    }

    /** Si el menú completo no deja el ancho mínimo para el área principal, queda solo con íconos. */
    private boolean wantsRail() {
        return scene.getWidth() > 0 && scene.getWidth() < Ui.px(SIDE_WIDTH + Page.MIN_WIDTH + 10);
    }

    private void updateRail() {
        if (wantsRail() == rail || !loggedIn || frame.getLeft() == null) {
            return;
        }
        rail = !rail;
        frame.setLeft(buildSideScroll());
        if (page != null) {
            markNav(page.nav());
        }
    }

    private ScrollPane buildSideScroll() {
        VBox side = buildSide();
        ScrollPane sideScroll = new ScrollPane(side);
        sideScroll.setFitToWidth(true);
        sideScroll.setFitToHeight(true);
        sideScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sideScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        double w = Ui.px(rail ? RAIL_WIDTH : SIDE_WIDTH);
        sideScroll.setMinWidth(w);
        sideScroll.setPrefWidth(w);
        sideScroll.setMaxWidth(w);
        sideScroll.heightProperty().addListener((obs, old, h) -> fitSide(side, h.doubleValue()));
        return sideScroll;
    }

    /**
     * En ventanas bajas el menú no cabe entero y se perdían "Cerrar sesión" y "Mi perfil".
     * Primero se achica la marca y luego se quita; los botones siempre quedan.
     */
    private static void fitSide(VBox side, double height) {
        if (height <= 0 || !(side.getChildren().get(0) instanceof VBox brand)
                || !(brand.getChildren().get(0) instanceof ImageView mono)) {
            return;
        }
        double logo = (double) mono.getProperties().get("sv-logo");
        var kids = brand.getChildren();
        for (int level = 0; level < 4; level++) {
            side.getStyleClass().remove("tight");
            if (level > 0) {
                side.getStyleClass().add("tight");
            }
            if (kids.size() > 2) {
                show(kids.get(2), level < 1);
            }
            if (kids.size() > 1) {
                show(kids.get(1), level < 2);
            }
            show(brand, level < 3);
            mono.setFitHeight(level < 2 ? logo : Math.min(logo, Ui.px(44)));
            side.applyCss();
            if (side.prefHeight(-1) <= height) {
                return;
            }
        }
    }

    private static void show(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private HBox buildStrip() {
        strip = new HBox();
        strip.getStyleClass().add("strip");
        return strip;
    }

    public void refreshStrip() {
        if (strip == null || !loggedIn) {
            return;
        }
        DashboardService.Summary s = ctx.s().dashboard().summary();
        strip.getChildren().setAll(
                stripLink(null, "Hoy", s.todayCount() == 1 ? "1 cita" : s.todayCount() + " citas", true,
                        () -> go(Nav.CITAS)),
                stripLink("dot-pend", String.valueOf(s.pendingCount()), "por avisar por WhatsApp", false,
                        () -> go(Nav.INICIO)),
                stripLink("dot-avis", String.valueOf(s.notifiedToday()),
                        s.notifiedToday() == 1 ? "sin respuesta de WhatsApp" : "sin respuesta de WhatsApp", false,
                        () -> goPacientes(PatientFilter.WhatsAppFilter.AVISADO)));
    }

    private Button stripLink(String dot, String bold, String rest, boolean boldFirstOnly, Runnable action) {
        HBox g = new HBox(Ui.px(boldFirstOnly ? 5 : 6));
        g.setAlignment(Pos.CENTER_LEFT);
        if (dot != null) {
            g.getChildren().add(Ui.dot(dot));
            HBox.setMargin(g.getChildren().get(0), new javafx.geometry.Insets(0, Ui.px(2), 0, 0));
        }
        g.getChildren().addAll(Ui.label(bold, "b"), new Label(rest));
        Button b = new Button();
        b.getStyleClass().setAll("button", "strip-link");
        b.setGraphic(g);
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        b.setAccessibleText(bold + " " + rest);
        b.setOnAction(e -> action.run());
        return b;
    }

    private VBox buildSide() {
        VBox side = new VBox();
        side.getStyleClass().add("side");
        if (rail) {
            side.getStyleClass().add("rail");
        }

        ImageView mono = new ImageView(Images.get("mono.png", darkApplied));
        mono.setPreserveRatio(true);
        mono.setSmooth(true);
        double logo = Ui.px(rail ? 40 : 74);
        mono.setFitHeight(logo);
        mono.getProperties().put("sv-logo", logo);
        VBox brand = new VBox(mono);
        if (!rail) {
            Label word = Ui.label(Ui.spaced("Stephany Valencia"), "brand-word");
            Label tag = Ui.label(Ui.spaced("Odontóloga ·") + "\n" + Ui.spaced("Armonizadora") + "\n"
                    + Ui.spaced("orofacial"), "brand-tag");
            tag.setTextAlignment(TextAlignment.CENTER);
            tag.setLineSpacing(Ui.px(2));
            tag.setMinHeight(Region.USE_PREF_SIZE);
            brand.getChildren().addAll(word, tag);
        }
        brand.getStyleClass().add("brand");
        brand.setCursor(javafx.scene.Cursor.HAND);
        brand.setOnMouseClicked(e -> go(Nav.INICIO));

        VBox navs = new VBox();
        navs.getStyleClass().add("navs");
        navButtons.clear();
        navs.getChildren().addAll(
                nav(Nav.INICIO, "Inicio", Icons.HOME),
                nav(Nav.CITAS, "Citas", Icons.CALENDAR),
                nav(Nav.PACIENTES, "Pacientes", Icons.USERS),
                nav(Nav.PROCESOS, "Procedimientos", Icons.LAYERS),
                nav(Nav.REPORTES, "Reportes", Icons.FILE));

        Button theme = footButton(darkApplied ? "Modo claro" : "Modo oscuro", darkApplied ? Icons.SUN : Icons.MOON);
        theme.setOnAction(e -> toggleDark());
        Button logout = footButton("Cerrar sesión", Icons.LOGOUT);
        logout.setOnAction(e -> logout());

        StackPane av = new StackPane(new Label("A"));
        av.getStyleClass().add("av");
        HBox meBox = new HBox(Ui.px(12), av);
        if (!rail) {
            meBox.getChildren().add(new VBox(Ui.label("Administrador", "mname"), Ui.label("Mi perfil", "mrole")));
        }
        meBox.setAlignment(Pos.CENTER_LEFT);
        Button me = new Button();
        me.getStyleClass().setAll("button", "me");
        me.setGraphic(meBox);
        me.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        me.setMaxWidth(Double.MAX_VALUE);
        me.setAlignment(rail ? Pos.CENTER : Pos.CENTER_LEFT);
        me.setAccessibleText("Mi perfil");
        if (rail) {
            Ui.tip(me, "Mi perfil");
        }
        me.setOnAction(e -> go(Nav.PERFIL));
        navButtons.put(Nav.PERFIL, me);

        VBox foot = new VBox(theme, logout, me);
        foot.getStyleClass().add("foot");

        side.getChildren().addAll(brand, navs, Ui.vspacer(), foot);
        return side;
    }

    private Button nav(Nav nav, String text, String icon) {
        Button b = new Button(text);
        b.getStyleClass().setAll("button", "nav");
        b.setGraphic(Icons.of(icon, 20, 1.6));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMinWidth(rail ? 0 : Ui.px(220));
        b.setWrapText(false);
        b.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        b.setMnemonicParsing(false);
        b.setOnAction(e -> go(nav));
        railOnly(b, text);
        navButtons.put(nav, b);
        return b;
    }

    private Button footButton(String text, String icon) {
        Button b = new Button(text);
        b.getStyleClass().setAll("button", "theme-btn");
        b.setGraphic(Icons.of(icon, 20, 1.6));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMnemonicParsing(false);
        railOnly(b, text);
        return b;
    }

    /** En el menú angosto el botón muestra solo el ícono y el nombre sale al pasar el ratón. */
    private void railOnly(Button b, String text) {
        if (rail) {
            b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            b.setAccessibleText(text);
            Ui.tip(b, text);
        }
    }

    private void markNav(Nav active) {
        navButtons.forEach((k, b) -> {
            b.getStyleClass().remove("on");
            if (k == active) {
                b.getStyleClass().add("on");
            }
        });
    }

    public Node content() {
        return page == null ? null : page.root();
    }
}
