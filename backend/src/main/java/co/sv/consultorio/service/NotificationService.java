package co.sv.consultorio.service;

import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.WaStatus;
import co.sv.consultorio.repo.AppointmentRepository;
import co.sv.consultorio.util.Fmt;
import co.sv.consultorio.util.Texts;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Avisos por WhatsApp. No hay API: se abre la aplicación de Windows con
 * {@code whatsapp://send?phone=57<tel>&text=...} y la usuaria envía el mensaje.
 * El aviso pertenece a la cita. Avisado = se envió el mensaje. Agendado = contestó.
 * No respondió permite enviar otro mensaje.
 */
public class NotificationService {

    public static final String COUNTRY_CODE = "57";

    private final AppointmentRepository appointments;
    private final TimeSource time;

    public NotificationService(AppointmentRepository appointments, TimeSource time) {
        this.appointments = appointments;
        this.time = time;
    }

    /** Mensaje predeterminado del proceso con los datos de la cita o del control. */
    public String defaultMessage(AppointmentView v) {
        return render(v.process(), v.patient(), Fmt.messageDate(v.appointment().startsAt()), v.appointment().type());
    }

    /** Mensaje del proceso para una fecha sin hora (cuando aún no hay cita). */
    public String defaultMessage(Patient p, Process proc, LocalDate date) {
        return render(proc, p, date == null ? "" : Fmt.longDate(date), AppointmentType.CITA);
    }

    private static String render(Process proc, Patient p, String fecha, AppointmentType type) {
        String template = proc == null ? MessageTemplates.DEFAULT_TEMPLATE : proc.messageTemplate();
        String tipo = type == null ? AppointmentType.CITA.inlineLabel() : type.inlineLabel();
        return MessageTemplates.render(template, p.fullName(), proc == null ? "" : proc.name(), fecha, tipo);
    }

    /**
     * Abre el chat en la aplicación de Windows, sin el texto en el enlace.
     * El protocolo {@code whatsapp://} de Windows estropea el texto; el mensaje se pega después, en Unicode.
     */
    public URI whatsAppDesktopUri(String phone) {
        return whatsAppDesktopUri(COUNTRY_CODE, phone);
    }

    /** Abre el chat con el indicativo guardado y el teléfono local. */
    public URI whatsAppDesktopUri(String countryCode, String phone) {
        return URI.create("whatsapp://send?phone=" + international(countryCode, phone));
    }

    public URI whatsAppUri(String phone, String text) {
        return whatsAppUri(COUNTRY_CODE, phone, text);
    }

    public URI whatsAppUri(String countryCode, String phone, String text) {
        String encoded = URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create("https://wa.me/" + international(countryCode, phone) + "?text=" + encoded);
    }

    private static String international(String countryCode, String phone) {
        String cc = Texts.countryCode(countryCode);
        return cc + Texts.localPhone(phone, cc);
    }

    /**
     * Marca la cita como avisada (el mensaje se envió).
     * Si ya está avisada o agendada, no deja enviar otro hasta registrar que no respondió.
     */
    public Appointment markNotified(long appointmentId, String text) {
        Appointment a = require(appointmentId);
        if (!a.waStatus().canSend()) {
            throw new BusinessException(blockedMessage(a));
        }
        if (!appointments.markNotified(appointmentId, time.now(), text)) {
            throw new BusinessException(blockedMessage(appointments.findById(appointmentId)));
        }
        return appointments.findById(appointmentId);
    }

    /** La persona contestó por WhatsApp. */
    public Appointment markScheduled(long appointmentId) {
        return outcome(appointmentId, WaStatus.AGENDADO);
    }

    /** No contestó. Después se le puede escribir de nuevo. */
    public Appointment markNoReply(long appointmentId) {
        return outcome(appointmentId, WaStatus.NO_RESPONDIO);
    }

    private Appointment outcome(long appointmentId, WaStatus status) {
        Appointment a = require(appointmentId);
        if (a.waStatus() != WaStatus.AVISADO) {
            throw new BusinessException(a.waStatus() == WaStatus.NONE
                    ? "Primero envía el mensaje de WhatsApp."
                    : "Esa cita ya está marcada como " + a.waStatus().label().toLowerCase() + ".");
        }
        if (!appointments.markOutcome(appointmentId, status, time.now())) {
            throw new BusinessException("No se pudo actualizar el aviso.");
        }
        return appointments.findById(appointmentId);
    }

    private Appointment require(long appointmentId) {
        Appointment a = appointments.findById(appointmentId);
        if (a == null) {
            throw new BusinessException("La cita ya no existe.");
        }
        return a;
    }

    public static String blockedMessage(Appointment a) {
        if (a.waStatus() == WaStatus.AGENDADO) {
            return "Ya quedó agendado: la persona respondió por WhatsApp.";
        }
        if (a.notifiedAt() == null) {
            return "Ya se registró el aviso de esta cita.";
        }
        return "Ya se le avisó el " + Fmt.longDate(a.notifiedAt().toLocalDate())
                + ". Registra si respondió o si no contestó.";
    }
}
