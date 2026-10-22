package co.sv.consultorio.service;

import co.sv.consultorio.domain.AppSettings;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.domain.NextStep;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.util.Fmt;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resúmenes de Inicio, franja superior y aviso diario. */
public class DashboardService {

    /** Pacientes sin cita con la 2.ª cita vencida hace más de estos días ya no aparecen en "por avisar". */
    public static final int OVERDUE_DAYS = 30;

    public record Summary(int todayCount, int pendingCount, int notifiedToday) {
    }

    /**
     * Paciente por avisar. Si tiene cita, {@code appointment} no es null; si no, {@code step} indica
     * la cita que le toca (2.ª sugerida o vencida).
     */
    public record PendingItem(PatientOverview overview, AppointmentView appointment, NextStep step, LocalDateTime when) {
        public boolean hasAppointment() {
            return appointment != null;
        }
    }

    public record Stats(List<AppointmentView> today, int weekCount, LocalDate weekLastDay, int monthCount,
                        int pendingCount) {
    }

    public record DayGroup(LocalDate date, List<AppointmentView> items) {
        public int pending() {
            return (int) items.stream().filter(v -> v.appointment().waStatus().canSend()).count();
        }
    }

    public record WeekGroup(LocalDate from, LocalDate to, String title, int count, int pending) {
    }

    public record Popup(List<AppointmentView> today, List<DayGroup> week, int weekCount,
                        List<WeekGroup> month, int monthCount) {
    }

    private final AppointmentService appointments;
    private final PatientService patients;
    private final SettingsService settings;
    private final TimeSource time;

    public DashboardService(AppointmentService appointments, PatientService patients, SettingsService settings,
                            TimeSource time) {
        this.appointments = appointments;
        this.patients = patients;
        this.settings = settings;
        this.time = time;
    }

    public Summary summary() {
        LocalDate today = time.today();
        List<AppointmentView> todayList = appointments.between(today, today);
        int awaiting = (int) awaitingReply().size();
        return new Summary(todayList.size(), pending().size(), awaiting);
    }

    public Stats stats() {
        LocalDate today = time.today();
        LocalDate sunday = endOfWeek(today);
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        return new Stats(
                appointments.between(today, today),
                appointments.between(today, sunday).size(),
                lastWorkDay(today, sunday),
                appointments.between(today, monthEnd).size(),
                pending().size());
    }

    /** Citas ya avisadas que todavía no tienen respuesta de WhatsApp. */
    public List<AppointmentView> awaitingReply() {
        LocalDate today = time.today();
        return appointments.between(today, today.plusYears(5)).stream()
                .filter(v -> v.appointment().waStatus().awaitingReply())
                .toList();
    }

    /** Citas marcadas como que no contestaron y se les puede escribir de nuevo. */
    public List<AppointmentView> noReply() {
        LocalDate today = time.today();
        return appointments.between(today, today.plusYears(5)).stream()
                .filter(v -> v.appointment().waStatus() == co.sv.consultorio.domain.WaStatus.NO_RESPONDIO)
                .toList();
    }

    /**
     * Pacientes por avisar: citas sin aviso dentro de los días de anticipación configurados,
     * y pacientes sin cita cuya 2.ª cita sugerida cae en esa ventana (o venció hace poco).
     */
    public List<PendingItem> pending() {
        LocalDate today = time.today();
        int days = settings.get().reminderDays();
        LocalDate horizon = today.plusDays(days);
        Map<Long, PatientOverview> overviews = patients.overviews().stream()
                .filter(o -> o.patient().active())
                .collect(Collectors.toMap(o -> o.patient().id(), Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<PendingItem> out = new ArrayList<>();
        for (AppointmentView v : appointments.between(today, horizon)) {
            if (v.appointment().waStatus().canSend() && overviews.containsKey(v.patient().id())) {
                out.add(new PendingItem(overviews.get(v.patient().id()), v, null, v.appointment().startsAt()));
            }
        }
        for (PatientOverview o : overviews.values()) {
            if (o.hasAppointment()) {
                continue;
            }
            NextStep step = o.nextStep(today);
            boolean due = step.kind() == NextStep.Kind.SUGGESTED || step.kind() == NextStep.Kind.OVERDUE;
            if (due && !step.date().isAfter(horizon) && !step.date().isBefore(today.minusDays(OVERDUE_DAYS))) {
                out.add(new PendingItem(o, null, step, step.date().atStartOfDay()));
            }
        }
        out.sort(Comparator.comparing(PendingItem::when));
        return out;
    }

    public Popup popup() {
        LocalDate today = time.today();
        LocalDate sunday = endOfWeek(today);
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        List<AppointmentView> monthList = appointments.between(today, monthEnd.isBefore(sunday) ? sunday : monthEnd);

        List<AppointmentView> todayList = monthList.stream()
                .filter(v -> v.appointment().startsAt().toLocalDate().equals(today)).toList();

        Map<LocalDate, List<AppointmentView>> byDay = new LinkedHashMap<>();
        for (AppointmentView v : monthList) {
            LocalDate d = v.appointment().startsAt().toLocalDate();
            if (!d.isAfter(sunday)) {
                byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(v);
            }
        }
        List<DayGroup> week = byDay.entrySet().stream().map(e -> new DayGroup(e.getKey(), e.getValue())).toList();
        int weekCount = week.stream().mapToInt(g -> g.items().size()).sum();

        List<WeekGroup> month = new ArrayList<>();
        int monthCount = 0;
        LocalDate start = today;
        int index = 0;
        while (!start.isAfter(monthEnd)) {
            LocalDate end = endOfWeek(start).isAfter(monthEnd) ? monthEnd : endOfWeek(start);
            LocalDate s = start;
            List<AppointmentView> inWeek = monthList.stream().filter(v -> {
                LocalDate d = v.appointment().startsAt().toLocalDate();
                return !d.isBefore(s) && !d.isAfter(end);
            }).toList();
            int pend = (int) inWeek.stream().filter(v -> v.appointment().waStatus().canSend()).count();
            String title = index == 0 ? "Esta semana"
                    : index == 1 ? "Próxima semana"
                    : end.equals(monthEnd) ? "Última semana"
                    : "Semana del " + Fmt.shortDate(start, start);
            month.add(new WeekGroup(start, end, title, inWeek.size(), pend));
            monthCount += inWeek.size();
            start = end.plusDays(1);
            index++;
        }
        return new Popup(todayList, week, weekCount, month, monthCount);
    }

    public static LocalDate endOfWeek(LocalDate d) {
        return d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    private LocalDate lastWorkDay(LocalDate from, LocalDate to) {
        AppSettings s = settings.get();
        for (LocalDate d = to; !d.isBefore(from); d = d.minusDays(1)) {
            if (s.workDays().contains(d.getDayOfWeek())) {
                return d;
            }
        }
        return to;
    }
}
