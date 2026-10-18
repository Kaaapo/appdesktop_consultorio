package co.sv.consultorio.service;

import co.sv.consultorio.TestEnv;
import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.Process;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {

    private final TestEnv env = new TestEnv();
    private final NotificationService notifications = env.s.notifications();

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void defaultMessageUsesProcessTemplateAndAppointmentDate() {
        Process b = env.s.processes().create("Proceso B", 1, MessageTemplates.DEFAULT_TEMPLATE);
        Patient p = env.patient("Camila", "Rojas", "1017220431", b, TestEnv.TODAY.minusMonths(1));
        Appointment a = env.s.appointments().assign(p.id(), AppointmentType.CITA,
                LocalDateTime.of(2026, 10, 16, 10, 30)).saved();
        String msg = notifications.defaultMessage(env.s.appointments().view(a.id()));
        assertEquals("Hola Camila Rojas, te recordamos tu cita de Proceso B el viernes 16 de octubre a las 10:30 a. m., "
                + "¿te queda bien esa fecha y hora?", msg);
    }

    @Test
    void controlMessageSaysControlNotCita() {
        Process b = env.s.processes().create("Proceso B", 1,
                "Hola {nombre}, ya se acerca tu segunda cita de {procedimiento}: {fecha}.");
        Patient p = env.patient("Paula", "Vélez", "1035778120", b, TestEnv.TODAY.minusMonths(1));
        Appointment a = env.s.appointments().add(p.id(), AppointmentType.CONTROL,
                LocalDateTime.of(2026, 10, 17, 14, 0)).saved();
        String msg = notifications.defaultMessage(env.s.appointments().view(a.id()));
        assertEquals("Hola Paula Vélez, ya se acerca tu control de Proceso B: sábado 17 de octubre a las 2:00 p. m..", msg);
        assertFalse(msg.toLowerCase(java.util.Locale.ROOT).contains("cita"));
    }

    @Test
    void tipoVariableFollowsAppointmentKind() {
        Process b = env.s.processes().create("Proceso B", 1, MessageTemplates.DEFAULT_TEMPLATE);
        Patient p = env.patient("Ana", "López", "1017220999", b, TestEnv.TODAY);
        Appointment control = env.s.appointments().add(p.id(), AppointmentType.CONTROL,
                LocalDateTime.of(2026, 10, 20, 9, 0)).saved();
        String msg = notifications.defaultMessage(env.s.appointments().view(control.id()));
        assertTrue(msg.contains("tu control de Proceso B"));
        assertFalse(msg.contains("{tipo}"));
    }

    @Test
    void whatsAppLinkHasCountryCodeAndEncodedText() {
        URI uri = notifications.whatsAppUri("310 555 0142", "Hola Camila, ¿te queda bien?");
        assertEquals("https://wa.me/573105550142?text=Hola%20Camila%2C%20%C2%BFte%20queda%20bien%3F", uri.toString());
        URI emoji = notifications.whatsAppUri("3105550142", "Hola " + new String(Character.toChars(0x1F497)));
        assertTrue(emoji.toString().contains("%F0%9F%92%97"));
        assertFalse(emoji.toString().chars().anyMatch(c -> c > 127));
        URI app = notifications.whatsAppDesktopUri("310 555 0142");
        assertEquals("whatsapp://send?phone=573105550142", app.toString());
        assertFalse(app.toString().contains("text="));
        assertFalse(app.toString().startsWith("http"));
    }

    @Test
    void appointmentCanOnlyBeNotifiedOnce() {
        Process b = env.process("Proceso B", 1);
        Patient p = env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        Appointment a = env.s.appointments().assign(p.id(), AppointmentType.CITA,
                LocalDateTime.of(2026, 10, 15, 8, 0)).saved();

        Appointment n = notifications.markNotified(a.id(), "Hola Laura");
        assertEquals(TestEnv.NOW, n.notifiedAt());
        assertEquals("Hola Laura", n.notifiedText());

        BusinessException e = assertThrows(BusinessException.class, () -> notifications.markNotified(a.id(), "otra vez"));
        assertTrue(e.getMessage().contains("Ya se le avisó el 13 de octubre"));
        assertEquals("Hola Laura", env.s.appointments().get(a.id()).notifiedText());
    }

    @Test
    void reschedulingAllowsNotifyingAgain() {
        Process b = env.process("Proceso B", 1);
        Patient p = env.patient("Laura", "Mejía", "43987210", b, TestEnv.TODAY);
        Appointment a = env.s.appointments().assign(p.id(), AppointmentType.CITA,
                LocalDateTime.of(2026, 10, 15, 8, 0)).saved();
        notifications.markNotified(a.id(), "Hola Laura");
        env.s.appointments().assign(p.id(), AppointmentType.CITA, LocalDateTime.of(2026, 10, 20, 9, 0));
        Appointment again = notifications.markNotified(a.id(), "Nueva fecha");
        assertEquals(co.sv.consultorio.domain.WaStatus.AVISADO, again.waStatus());
        assertEquals("Nueva fecha", again.notifiedText());
    }

    @Test
    void templateVariables() {
        assertEquals("Hola Ana, Proceso X el 1 de enero",
                MessageTemplates.render("Hola {nombre}, {proceso} el {fecha}", "Ana", "Proceso X", "1 de enero"));
    }
}
