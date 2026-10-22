package co.sv.consultorio.service;

import co.sv.consultorio.TestEnv;
import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.ValidationException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Casos límite que se revisan antes de llevar el programa al consultorio. */
class DeliveryChecklistTest {

    private final TestEnv env = new TestEnv();

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void patientFormRejectsEmptyAndExtremeValuesButKeepsALongName() {
        Process b = env.process("Proceso B", 1);
        assertTrue(fails(new PatientInput("   ", "12345678", "3105550142", b.id(), yearsAgo(30))).fieldErrors().containsKey("fullName"));
        assertTrue(fails(new PatientInput("Ana", "123", "3105550142", b.id(), yearsAgo(30))).fieldErrors().containsKey("document"));
        assertTrue(fails(new PatientInput("Ana", "1".repeat(16), "3105550142", b.id(), yearsAgo(30))).fieldErrors().containsKey("document"));
        assertTrue(fails(new PatientInput("Ana", "12345678", "310555014", b.id(), yearsAgo(30))).fieldErrors().containsKey("phone"));
        assertTrue(fails(new PatientInput("Ana", "12345678", "31055501421", b.id(), yearsAgo(30))).fieldErrors().containsKey("phone"));
        assertTrue(fails(new PatientInput("Ana", "12345678", "12345", List.of(b.id()), yearsAgo(30), "1")).fieldErrors().containsKey("phone"));
        assertTrue(fails(new PatientInput("Ana", "12345678", "1".repeat(16), List.of(b.id()), yearsAgo(30), "1")).fieldErrors().containsKey("phone"));
        assertTrue(fails(new PatientInput("Ana", "12345678", "3105550142", b.id(), TestEnv.TODAY.plusDays(1))).fieldErrors().containsKey("birthDate"));
        assertTrue(fails(new PatientInput("Ana", "12345678", "3105550142", b.id(), yearsAgo(121))).fieldErrors().containsKey("birthDate"));
        assertTrue(fails(new PatientInput("Ana", "12345678", "3105550142", null, yearsAgo(30))).fieldErrors().containsKey("processId"));

        String huge = "Á".repeat(9000);
        Patient saved = env.s.patients().create(new PatientInput(huge, "1234", "3105550142", List.of(b.id()), yearsAgo(120), ""));
        assertEquals(huge, saved.fullName());
        assertEquals("1234", saved.document());
        assertEquals("57", saved.countryCode());
        assertEquals(yearsAgo(120), saved.birthDate());

        Patient foreign = env.s.patients().create(new PatientInput("Luis", "12345", "123456", List.of(b.id()), yearsAgo(40), "1"));
        assertEquals("1", foreign.countryCode());
        assertEquals("123456", foreign.phone());
    }

    @Test
    void scheduleGapReassignAndSecondAppointment() {
        Process b = env.process("Proceso B", 1);
        Process other = env.process("Proceso A", 3);
        Patient camila = env.patient("Camila", "Rojas", "1017220431", b, yearsAgo(30));
        LocalDateTime at = LocalDateTime.of(2026, 10, 16, 10, 30);
        Appointment first = env.s.appointments().add(camila.id(), AppointmentType.CITA, at, b.id()).saved();
        assertThrows(BusinessException.class,
                () -> env.s.appointments().add(camila.id(), AppointmentType.CITA, at.plusMinutes(60), b.id()));

        List<AppointmentService.Slot> moving = env.s.appointments().daySlots(at.toLocalDate(), first.id());
        assertEquals(AppointmentService.SlotState.BUSY, state(moving, LocalTime.of(10, 30)));
        assertEquals(AppointmentService.SlotState.FREE, state(moving, LocalTime.of(12, 0)));
        assertEquals(AppointmentService.SlotState.FREE, state(moving, LocalTime.of(7, 0)));
        assertEquals(AppointmentService.SlotState.FREE, state(moving, LocalTime.of(19, 0)));
        assertTrue(moving.stream().noneMatch(s -> s.time().equals(LocalTime.of(19, 30))));
        assertTrue(env.s.appointments().daySlots(TestEnv.TODAY.plusDays(5)).isEmpty());

        Appointment later = env.s.appointments().add(camila.id(), AppointmentType.CITA, at.plusMinutes(90), other.id()).saved();
        Appointment moved = env.s.appointments().reschedule(first.id(), AppointmentType.CITA, at.withHour(16).withMinute(0)).saved();
        assertEquals(first.id(), moved.id());
        assertEquals(LocalTime.of(16, 0), moved.startsAt().toLocalTime());
        var visits = env.s.appointments().forPatient(camila.id());
        assertEquals(2, visits.size());
        assertEquals(LocalTime.of(16, 0), visits.stream().filter(v -> v.appointment().id() == first.id())
                .findFirst().orElseThrow().appointment().startsAt().toLocalTime());
        assertEquals(at.plusMinutes(90), visits.stream().filter(v -> v.appointment().id() == later.id())
                .findFirst().orElseThrow().appointment().startsAt());
    }

    @Test
    void excelSkipsBadRowsAndKeepsAPatientWithoutProcedure() {
        env.process("Proceso B", 1);
        var wb = new XSSFWorkbook();
        var sh = wb.createSheet();
        row(sh, 0, "Documento", "Paciente", "Fecha de nacimiento", "indicativo", "telefono", "Procedimiento");
        row(sh, 1, "444555", "Sin Proc", "1990-10-13", "57", "3105550142", "");
        row(sh, 2, "444666", "Sin Ind", "1990-10-13", "", "3105550142", "");
        row(sh, 3, "444777", "Mal Proc", "1990-10-13", "57", "3105550142", "No Existe");

        var preview = env.s.imports().parse("lista.xlsx", wb);
        assertTrue(preview.rows().get(0).valid(), preview.rows().get(0).errors().toString());
        assertTrue(preview.rows().get(1).errors().contains("Falta el indicativo."));
        assertTrue(preview.rows().get(2).errors().get(0).contains("No Existe"));
        assertEquals(1, env.s.imports().commit(preview));
        Patient saved = env.s.patients().duplicateOf("444555", 0);
        assertEquals("Sin Proc", saved.fullName());
        assertNull(saved.processId());
        assertEquals(LocalDate.of(1990, 10, 13), saved.birthDate());
    }

    private ValidationException fails(PatientInput in) {
        return assertThrows(ValidationException.class, () -> env.s.patients().create(in));
    }

    private static LocalDate yearsAgo(int years) {
        return TestEnv.TODAY.minusYears(years);
    }

    private static AppointmentService.SlotState state(List<AppointmentService.Slot> slots, LocalTime time) {
        return slots.stream().filter(s -> s.time().equals(time)).findFirst().orElseThrow().state();
    }

    private static void row(org.apache.poi.ss.usermodel.Sheet sh, int r, String... values) {
        var row = sh.createRow(r);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
