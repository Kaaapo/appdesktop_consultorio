package co.sv.consultorio.service;

import co.sv.consultorio.TestEnv;
import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.NextStep;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentServiceTest {

    private static final LocalDateTime FRI_1030 = LocalDateTime.of(2026, 10, 16, 10, 30);

    private final TestEnv env = new TestEnv();
    private final AppointmentService appts = env.s.appointments();
    private final Process b = env.process("Proceso B", 1);
    private final Patient camila = env.patient("Camila", "Rojas", "1017220431", b, TestEnv.TODAY.minusMonths(1));

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void assignCreatesAppointmentAndSyncsSecondDate() {
        AppointmentService.AssignResult r = appts.assign(camila.id(), AppointmentType.CITA, FRI_1030);
        assertFalse(r.rescheduled());
        assertEquals(FRI_1030, appts.current(camila.id()).startsAt());
        assertEquals(1, env.s.patients().overview(camila.id()).citaCount());
        assertEquals(1, appts.view(r.saved().id()).citaNumber());
    }

    @Test
    void reassignReschedulesAndClearsNotification() {
        AppointmentService.AssignResult first = appts.assign(camila.id(), AppointmentType.CITA, FRI_1030);
        env.s.notifications().markNotified(first.saved().id(), "Hola Camila");
        assertEquals(co.sv.consultorio.domain.WaStatus.AVISADO, appts.get(first.saved().id()).waStatus());

        LocalDateTime newDate = FRI_1030.plusDays(1).withHour(9).withMinute(0);
        AppointmentService.AssignResult second = appts.assign(camila.id(), AppointmentType.CITA, newDate);
        assertTrue(second.rescheduled());
        Appointment a = appts.get(first.saved().id());
        assertEquals(newDate, a.startsAt());
        assertNull(a.notifiedAt());
        assertNull(a.notifiedText());
        assertEquals(co.sv.consultorio.domain.WaStatus.NONE, a.waStatus());
        assertEquals(1, appts.forPatient(camila.id()).stream()
                .filter(v -> !v.appointment().startsAt().isBefore(TestEnv.TODAY.atStartOfDay())).count());
    }

    @Test
    void undoRestoresPreviousAppointmentAndNotification() {
        AppointmentService.AssignResult first = appts.assign(camila.id(), AppointmentType.CITA, FRI_1030);
        env.s.notifications().markNotified(first.saved().id(), "Hola Camila");
        AppointmentService.AssignResult moved = appts.assign(camila.id(), AppointmentType.CONTROL, FRI_1030.plusDays(3));
        appts.undo(moved);
        Appointment a = appts.get(first.saved().id());
        assertEquals(FRI_1030, a.startsAt());
        assertEquals(AppointmentType.CITA, a.type());
        assertEquals(co.sv.consultorio.domain.WaStatus.AVISADO, a.waStatus());
    }

    @Test
    void undoNewAppointmentDeletesIt() {
        AppointmentService.AssignResult r = appts.assign(camila.id(), AppointmentType.CITA, FRI_1030);
        appts.undo(r);
        assertNull(appts.current(camila.id()));
        assertEquals(0, env.s.patients().overview(camila.id()).citaCount());
    }

    @Test
    void takenSlotIsRejected() {
        Patient other = env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        appts.assign(camila.id(), AppointmentType.CITA, FRI_1030);
        BusinessException e = assertThrows(BusinessException.class,
                () -> appts.assign(other.id(), AppointmentType.CITA, FRI_1030));
        assertTrue(e.getMessage().contains("hora y media"));
        assertThrows(BusinessException.class,
                () -> appts.assign(other.id(), AppointmentType.CITA, FRI_1030.plusMinutes(60)));
        appts.assign(other.id(), AppointmentType.CITA, FRI_1030.plusMinutes(90));
        assertEquals(FRI_1030.plusMinutes(90), appts.current(other.id()).startsAt());
    }

    @Test
    void inactivePatientFreesSlot() {
        Patient other = env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        appts.assign(camila.id(), AppointmentType.CITA, FRI_1030);
        env.s.patients().deactivate(camila.id());
        appts.assign(other.id(), AppointmentType.CONTROL, FRI_1030);
        assertEquals(FRI_1030, appts.current(other.id()).startsAt());
    }

    @Test
    void pastTimeIsRejected() {
        assertThrows(BusinessException.class,
                () -> appts.assign(camila.id(), AppointmentType.CITA, TestEnv.TODAY.atTime(9, 0)));
    }

    @Test
    void appointmentEarlierTodayIsKeptWhenAssigningANewOne() {
        env.clock.set(TestEnv.TODAY.atTime(7, 0));
        AppointmentService.AssignResult morning = appts.assign(camila.id(), AppointmentType.CITA, TestEnv.TODAY.atTime(9, 0));
        env.clock.set(TestEnv.NOW);
        AppointmentService.AssignResult control = appts.assign(camila.id(), AppointmentType.CONTROL, FRI_1030);
        assertFalse(control.rescheduled());
        assertNotEquals(morning.saved().id(), control.saved().id());
        assertEquals(2, appts.forPatient(camila.id()).size());
    }

    @Test
    void daySlotsMarkBusyAndPast() {
        Patient other = env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        appts.assign(other.id(), AppointmentType.CITA, TestEnv.TODAY.atTime(15, 30));
        List<AppointmentService.Slot> slots = appts.daySlots(TestEnv.TODAY);
        assertEquals(AppointmentService.SlotState.PAST, state(slots, LocalTime.of(9, 0)));
        assertEquals(AppointmentService.SlotState.FREE, state(slots, LocalTime.of(14, 0)));
        assertEquals(AppointmentService.SlotState.BUSY, state(slots, LocalTime.of(15, 30)));
    }

    @Test
    void rescheduleOpensHoursBlockedOnlyByThatAppointment() {
        Appointment saved = appts.assign(camila.id(), AppointmentType.CITA, FRI_1030).saved();
        LocalDate friday = FRI_1030.toLocalDate();
        assertEquals(AppointmentService.SlotState.BUSY, state(appts.daySlots(friday), LocalTime.of(10, 0)));
        assertEquals(AppointmentService.SlotState.BUSY, state(appts.daySlots(friday), LocalTime.of(11, 30)));

        List<AppointmentService.Slot> moving = appts.daySlots(friday, saved.id());
        assertEquals(AppointmentService.SlotState.BUSY, state(moving, LocalTime.of(10, 30)));
        assertEquals(AppointmentService.SlotState.FREE, state(moving, LocalTime.of(10, 0)));
        assertEquals(AppointmentService.SlotState.FREE, state(moving, LocalTime.of(9, 30)));
        assertEquals(AppointmentService.SlotState.FREE, state(moving, LocalTime.of(11, 30)));

        Patient other = env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        appts.assign(other.id(), AppointmentType.CITA, FRI_1030.withHour(15).withMinute(0));
        List<AppointmentService.Slot> still = appts.daySlots(friday, saved.id());
        assertEquals(AppointmentService.SlotState.BUSY, state(still, LocalTime.of(14, 30)));
        assertEquals(AppointmentService.SlotState.FREE, state(still, LocalTime.of(10, 0)));

        appts.reschedule(saved.id(), AppointmentType.CITA, FRI_1030.withHour(10).withMinute(0));
        assertEquals(LocalTime.of(10, 0), appts.get(saved.id()).startsAt().toLocalTime());
    }

    @Test
    void firstFreeSlotsSkipSundays() {
        List<LocalDateTime> free = appts.firstFreeSlots(6);
        assertEquals(6, free.size());
        assertEquals(TestEnv.TODAY.atTime(14, 0), free.get(0));
        assertTrue(free.stream().noneMatch(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY));
    }

    @Test
    void monthCountsByDay() {
        Patient other = env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        appts.assign(camila.id(), AppointmentType.CITA, FRI_1030);
        appts.assign(other.id(), AppointmentType.CITA, FRI_1030.withHour(8).withMinute(0));
        assertEquals(2, appts.monthCounts(YearMonth.of(2026, 10)).get(FRI_1030.toLocalDate()));
    }

    @Test
    void assigningFirstAppointmentRecalculatesSecondDate() {
        Patient manuela = env.patient("Manuela", "Zapata", "1007882310", b, TestEnv.TODAY.plusDays(5));
        LocalDateTime at = TestEnv.TODAY.plusDays(7).atTime(9, 0);
        appts.assign(manuela.id(), AppointmentType.CITA, at);
        PatientOverview manuelaView = env.s.patients().overview(manuela.id());
        assertEquals(1, manuelaView.citaCount());
        assertEquals(at.toLocalDate().plusMonths(1), manuelaView.nextStep(TestEnv.TODAY).date());
    }

    @Test
    void nextStepForPatientsWithoutAppointment() {
        Patient manuela = env.patient("Manuela", "Zapata", "1007882310", b, TestEnv.TODAY.plusDays(5));
        PatientOverview m = env.s.patients().overview(manuela.id());
        assertEquals(NextStep.Kind.NONE, m.nextStep(TestEnv.TODAY).kind());

        env.clock.set(TestEnv.TODAY.minusMonths(1).atTime(8, 0));
        appts.add(camila.id(), AppointmentType.CITA, TestEnv.TODAY.minusMonths(1).atTime(10, 0));
        env.clock.set(TestEnv.NOW);
        PatientOverview c = env.s.patients().overview(camila.id());
        assertEquals(1, c.citaCount());
        assertEquals(NextStep.Kind.SUGGESTED, c.nextStep(TestEnv.TODAY).kind());
        assertEquals("Cita 2 sugerida: 13 oct", c.nextStep(TestEnv.TODAY).note(2));
        assertEquals(NextStep.Kind.OVERDUE, c.nextStep(TestEnv.TODAY.plusDays(1)).kind());
        assertEquals("Cita 2 vencida desde el 13 oct", c.nextStep(TestEnv.TODAY.plusDays(1)).note(2));
    }

    private static AppointmentService.SlotState state(List<AppointmentService.Slot> slots, LocalTime t) {
        return slots.stream().filter(s -> s.time().equals(t)).findFirst().orElseThrow().state();
    }
}
