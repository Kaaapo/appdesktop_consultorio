package co.sv.consultorio.service;

import co.sv.consultorio.TestEnv;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientServiceTest {

    private final TestEnv env = new TestEnv();
    private final PatientService patients = env.s.patients();

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void createNormalizesDocumentAndPhone() {
        Process b = env.process("Proceso B", 1);
        Patient p = patients.create(new PatientInput("  Valeria Acosta ", "1.020.553 198", "+57 321 555 0187",
                b.id(), TestEnv.TODAY.minusYears(28)));
        assertEquals("1020553198", p.document());
        assertEquals("3215550187", p.phone());
        assertEquals("Valeria Acosta", p.fullName());
        assertEquals(28, p.age(TestEnv.TODAY));
        assertEquals(TestEnv.NOW, p.registeredAt());
        assertTrue(p.active());
    }

    @Test
    void phoneWith0057PrefixIsNormalized() {
        Process b = env.process("Proceso B", 1);
        Patient p = patients.create(new PatientInput("Valeria Acosta", "1020553198", "0057 321-555-0187",
                b.id(), TestEnv.TODAY.minusYears(28)));
        assertEquals("3215550187", p.phone());
    }

    @Test
    void duplicateDocumentIsRejectedWithPatientName() {
        Process b = env.process("Proceso B", 1);
        env.patient("Camila", "Rojas", "1017220431", b, TestEnv.TODAY);
        ValidationException e = assertThrows(ValidationException.class,
                () -> env.patient("Otra", "Persona", "1.017.220.431", b, TestEnv.TODAY));
        assertTrue(e.fieldErrors().get("document").contains("Camila Rojas"));
        assertNotNull(patients.duplicateOf("1017220431", 0));
    }

    @Test
    void duplicateIncludesInactivePatients() {
        Process b = env.process("Proceso B", 1);
        Patient p = env.patient("Camila", "Rojas", "1017220431", b, TestEnv.TODAY);
        patients.deactivate(p.id());
        ValidationException e = assertThrows(ValidationException.class,
                () -> env.patient("Otra", "Persona", "1017220431", b, TestEnv.TODAY));
        assertTrue(e.fieldErrors().get("document").contains("inactivo"));
    }

    @Test
    void updateKeepsOwnDocumentAndRegistrationDate() {
        Process b = env.process("Proceso B", 1);
        Patient p = env.patient("Camila", "Rojas", "1017220431", b, TestEnv.TODAY);
        env.clock.set(TestEnv.NOW.plusDays(3));
        Patient u = patients.update(p.id(), new PatientInput("Camila Rojas Díaz", "1017220431", "3105550142",
                b.id(), TestEnv.TODAY.minusYears(30)));
        assertEquals("Camila Rojas Díaz", u.fullName());
        assertEquals(p.registeredAt(), u.registeredAt());
        assertEquals(30, u.age(TestEnv.TODAY));
    }

    @Test
    void requiredFieldsAndPhoneLength() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> patients.create(new PatientInput("", "", "123", null, null)));
        assertTrue(e.fieldErrors().keySet().containsAll(List.of("fullName", "document", "phone", "processId", "birthDate")));
    }

    @Test
    void futureBirthDateIsRejected() {
        Process b = env.process("Proceso B", 1);
        ValidationException e = assertThrows(ValidationException.class, () -> patients.create(new PatientInput(
                "Ana Uno", "123456", "3105550142", b.id(), TestEnv.TODAY.plusDays(1))));
        assertTrue(e.fieldErrors().containsKey("birthDate"));
    }

    @Test
    void suggestedNextDateUsesProcessMonths() {
        Process c = env.process("Proceso C", 3);
        assertEquals(LocalDate.of(2027, 1, 13), patients.suggestedNextDate(TestEnv.TODAY, c.id()));
        assertEquals(LocalDate.of(2027, 2, 28),
                patients.suggestedNextDate(LocalDate.of(2026, 11, 30), c.id()));
    }

    @Test
    void listIsMostRecentFirstAndHidesInactive() {
        Process b = env.process("Proceso B", 1);
        Patient old = env.patient("Ana", "Uno", "1111", b, TestEnv.TODAY);
        env.clock.set(TestEnv.NOW.plusMinutes(5));
        Patient recent = env.patient("Bea", "Dos", "2222", b, TestEnv.TODAY);
        List<PatientOverview> list = patients.search(PatientFilter.all());
        assertEquals(recent.id(), list.get(0).patient().id());

        patients.deactivate(old.id());
        assertEquals(1, patients.search(PatientFilter.all()).size());
        List<PatientOverview> inactive = patients.search(new PatientFilter(null, null, PatientFilter.CitaFilter.ALL,
                PatientFilter.WhatsAppFilter.ALL, null, null, true, false));
        assertEquals(old.id(), inactive.get(0).patient().id());

        patients.reactivate(old.id());
        assertEquals(2, patients.search(PatientFilter.all()).size());
    }

    @Test
    void searchByNameWithoutAccentsAndByDocument() {
        Process b = env.process("Proceso B", 1);
        env.patient("Sofía", "Restrepo", "1152331670", b, TestEnv.TODAY);
        env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        assertEquals("Sofía Restrepo", search("sofia").get(0).patient().fullName());
        assertEquals("Laura Mejía", search("MEJIA").get(0).patient().fullName());
        assertEquals("Laura Mejía", search("43.987").get(0).patient().fullName());
        assertTrue(search("zzz").isEmpty());
    }

    @Test
    void searchIgnoresExtraSpaces() {
        Process b = env.process("Proceso B", 1);
        env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        assertEquals(1, search("  laura   MEJIA ").size());
    }

    @Test
    void filterByProcessAndRegistrationRange() {
        Process a = env.process("Proceso A", 2);
        Process b = env.process("Proceso B", 1);
        env.patient("Ana", "Uno", "1111", a, TestEnv.TODAY);
        env.clock.set(TestEnv.NOW.plusDays(10));
        env.patient("Bea", "Dos", "2222", b, TestEnv.TODAY);
        assertEquals(1, patients.search(new PatientFilter(null, a.id(), PatientFilter.CitaFilter.ALL,
                PatientFilter.WhatsAppFilter.ALL, null, null, false, false)).size());
        List<PatientOverview> inRange = patients.search(new PatientFilter(null, null, PatientFilter.CitaFilter.ALL,
                PatientFilter.WhatsAppFilter.ALL, TestEnv.TODAY.plusDays(5), TestEnv.TODAY.plusDays(10), false, false));
        assertEquals(1, inRange.size());
        assertEquals("Bea Dos", inRange.get(0).patient().fullName());
        assertFalse(inRange.get(0).hasAppointment());
    }

    private List<PatientOverview> search(String q) {
        return patients.search(new PatientFilter(q, null, PatientFilter.CitaFilter.ALL,
                PatientFilter.WhatsAppFilter.ALL, null, null, false, false));
    }
}
