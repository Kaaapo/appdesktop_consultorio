package co.sv.consultorio.service;

import co.sv.consultorio.TestEnv;
import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.Process;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceTest {

    private final TestEnv env = new TestEnv();
    private final DashboardService dashboard = env.s.dashboard();
    private Patient mariana;
    private Patient isabela;
    private Patient camila;
    private Patient lejana;

    @BeforeEach
    void seed() {
        Process b = env.process("Proceso B", 1);
        env.clock.set(TestEnv.TODAY.atTime(7, 0));
        mariana = env.patient("Mariana", "Gómez", "1128905774", b, TestEnv.TODAY);
        isabela = env.patient("Isabela", "Torres", "1017334562", b, TestEnv.TODAY);
        Patient luisa = env.patient("Luisa", "Ortiz", "1036998812", b, TestEnv.TODAY);
        camila = env.patient("Camila", "Rojas", "1017220431", b, LocalDate.of(2026, 9, 16));
        env.clock.set(LocalDateTime.of(2026, 9, 16, 8, 0));
        env.s.appointments().add(camila.id(), AppointmentType.CITA, LocalDateTime.of(2026, 9, 16, 10, 0));
        env.clock.set(TestEnv.TODAY.atTime(7, 0));
        lejana = env.patient("Lejana", "Pérez", "99887766", b, TestEnv.TODAY);

        Appointment m = assign(mariana, LocalDateTime.of(2026, 10, 13, 9, 0));
        assign(isabela, LocalDateTime.of(2026, 10, 13, 15, 30));
        Appointment l = assign(luisa, LocalDateTime.of(2026, 10, 16, 9, 0));
        assign(lejana, LocalDateTime.of(2026, 10, 30, 9, 0));
        env.s.notifications().markNotified(m.id(), "ok");
        env.clock.set(TestEnv.NOW);
        env.s.notifications().markNotified(l.id(), "ok");
    }

    @AfterEach
    void close() {
        env.close();
    }

    private Appointment assign(Patient p, LocalDateTime at) {
        return env.s.appointments().assign(p.id(), AppointmentType.CITA, at).saved();
    }

    @Test
    void summaryCounts() {
        DashboardService.Summary s = dashboard.summary();
        assertEquals(2, s.todayCount());
        assertEquals(2, s.notifiedToday());
        assertEquals(2, s.pendingCount());
    }

    @Test
    void pendingIncludesUnnotifiedInWindowAndPatientsWithoutAppointment() {
        List<DashboardService.PendingItem> pending = dashboard.pending();
        assertEquals(2, pending.size());
        assertEquals(isabela.id(), pending.get(0).overview().patient().id());
        assertTrue(pending.get(0).hasAppointment());
        assertEquals(camila.id(), pending.get(1).overview().patient().id());
        assertFalse(pending.get(1).hasAppointment());
        assertTrue(pending.stream().noneMatch(p -> p.overview().patient().id() == lejana.id()));
        assertTrue(pending.stream().noneMatch(p -> p.overview().patient().id() == mariana.id()));
    }

    @Test
    void reminderDaysWidenTheWindow() {
        env.s.settings().save(env.s.settings().get().withReminderDays(30));
        assertTrue(dashboard.pending().stream().anyMatch(p -> p.overview().patient().id() == lejana.id()));
    }

    @Test
    void inactivePatientsAreNotPending() {
        env.s.patients().deactivate(isabela.id());
        env.s.patients().deactivate(camila.id());
        assertTrue(dashboard.pending().isEmpty());
        assertEquals(1, dashboard.summary().todayCount());
    }

    @Test
    void statsAndPopupGroups() {
        DashboardService.Stats st = dashboard.stats();
        assertEquals(2, st.today().size());
        assertEquals(3, st.weekCount());
        assertEquals(LocalDate.of(2026, 10, 17), st.weekLastDay());
        assertEquals(4, st.monthCount());

        DashboardService.Popup p = dashboard.popup();
        assertEquals(2, p.today().size());
        assertEquals(2, p.week().size());
        assertEquals(3, p.weekCount());
        assertEquals(4, p.monthCount());
        assertEquals("Esta semana", p.month().get(0).title());
        assertEquals(LocalDate.of(2026, 10, 18), p.month().get(0).to());
        assertEquals("Próxima semana", p.month().get(1).title());
        assertEquals("Última semana", p.month().get(p.month().size() - 1).title());
        assertEquals(1, p.month().get(0).pending());
    }

    @Test
    void notifiedUpcomingList() {
        assertEquals(2, dashboard.awaitingReply().size());
    }
}
