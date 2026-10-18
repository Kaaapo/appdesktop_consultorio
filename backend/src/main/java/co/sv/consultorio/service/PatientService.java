package co.sv.consultorio.service;

import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.ValidationException;
import co.sv.consultorio.domain.WaStatus;
import co.sv.consultorio.repo.AppointmentRepository;
import co.sv.consultorio.repo.PatientRepository;
import co.sv.consultorio.repo.ProcessRepository;
import co.sv.consultorio.util.Texts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PatientService {

    public static final int MIN_DOCUMENT = 4;
    public static final int MAX_DOCUMENT = 15;

    private final PatientRepository patients;
    private final ProcessRepository processes;
    private final AppointmentRepository appointments;
    private final TimeSource time;

    public PatientService(PatientRepository patients, ProcessRepository processes,
                          AppointmentRepository appointments, TimeSource time) {
        this.patients = patients;
        this.processes = processes;
        this.appointments = appointments;
        this.time = time;
    }

    public Patient get(long id) {
        return patients.findById(id);
    }

    public int countActive() {
        return patients.countActive();
    }

    /** Fecha sugerida de la siguiente cita: última cita + meses del procedimiento. */
    public LocalDate suggestedNextDate(LocalDate lastCita, Long processId) {
        if (processId == null || lastCita == null) {
            return null;
        }
        Process p = processes.findById(processId);
        return p == null ? null : lastCita.plusMonths(p.monthsToSecond());
    }

    /** Paciente que ya usa ese documento (sin contar a {@code excludeId}), o null. */
    public Patient duplicateOf(String document, long excludeId) {
        String doc = Texts.digitsOnly(document);
        if (doc.isEmpty()) {
            return null;
        }
        Patient p = patients.findByDocument(doc);
        return p != null && p.id() != excludeId ? p : null;
    }

    public Patient create(PatientInput in) {
        Patient p = validate(0, in, null);
        Patient toSave = new Patient(0, p.fullName(), p.document(), p.phone(), p.processId(),
                p.birthDate(), time.now(), true);
        long id = patients.insert(toSave);
        return patients.findById(id);
    }

    public Patient update(long id, PatientInput in) {
        Patient current = patients.findById(id);
        if (current == null) {
            throw new BusinessException("El paciente ya no existe.");
        }
        Patient p = validate(id, in, current);
        patients.update(new Patient(id, p.fullName(), p.document(), p.phone(), p.processId(),
                p.birthDate(), current.registeredAt(), current.active()));
        return patients.findById(id);
    }

    public void deactivate(long id) {
        patients.setActive(id, false);
    }

    public void reactivate(long id) {
        patients.setActive(id, true);
    }

    /**
     * Valida y normaliza los datos. Documento y teléfono quedan solo con dígitos.
     * Lanza {@link ValidationException} con los errores por campo.
     */
    Patient validate(long id, PatientInput in, Patient current) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = Texts.cleanName(in.fullName());
        String doc = Texts.digitsOnly(in.document());
        String phone = Texts.normalizePhone(in.phone());

        if (name.isEmpty()) {
            errors.put("fullName", "Escribe el nombre completo.");
        }
        if (doc.isEmpty()) {
            errors.put("document", "Escribe el número de documento.");
        } else if (doc.length() < MIN_DOCUMENT || doc.length() > MAX_DOCUMENT) {
            errors.put("document", "El documento debe tener entre " + MIN_DOCUMENT + " y " + MAX_DOCUMENT + " dígitos.");
        } else {
            Patient dup = duplicateOf(doc, id);
            if (dup != null) {
                errors.put("document", "Ya existe un paciente con este documento: " + dup.fullName()
                        + (dup.active() ? "." : " (inactivo)."));
            }
        }
        if (phone.isEmpty()) {
            errors.put("phone", "Escribe el teléfono.");
        } else if (phone.length() != 10) {
            errors.put("phone", "El teléfono debe tener 10 dígitos (celular).");
        }
        if (in.processId() == null || processes.findById(in.processId()) == null) {
            errors.put("processId", "Elige el procedimiento.");
        }
        if (in.birthDate() == null) {
            errors.put("birthDate", "Elige la fecha de nacimiento.");
        } else if (in.birthDate().isAfter(time.today())) {
            errors.put("birthDate", "La fecha de nacimiento no puede ser futura.");
        } else if (in.birthDate().isBefore(time.today().minusYears(120))) {
            errors.put("birthDate", "Revisa la fecha de nacimiento.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Patient(id, name, doc, phone, in.processId(), in.birthDate(),
                current == null ? null : current.registeredAt(), current == null || current.active());
    }

    public PatientOverview overview(long id) {
        return overviews().stream().filter(o -> o.patient().id() == id).findFirst().orElse(null);
    }

    /** Todos los pacientes (activos e inactivos) con su cita vigente, más recientes primero. */
    public List<PatientOverview> overviews() {
        LocalDate today = time.today();
        Map<Long, Process> procs = processes.findAll().stream()
                .collect(Collectors.toMap(Process::id, Function.identity()));
        Map<Long, Appointment> current = appointments.findCurrentByPatient(today);
        Map<Long, Integer> counts = appointments.citaCountByPatient();
        Map<Long, Integer> controls = appointments.controlCountByPatient();
        Map<Long, LocalDate> last = appointments.lastCitaByPatient();
        List<PatientOverview> out = new ArrayList<>();
        for (Patient p : patients.findAll()) {
            out.add(new PatientOverview(p, procs.get(p.processId()), current.get(p.id()),
                    counts.getOrDefault(p.id(), 0), controls.getOrDefault(p.id(), 0), last.get(p.id())));
        }
        return out;
    }

    public List<PatientOverview> search(PatientFilter f) {
        return filter(overviews(), f);
    }

    public static List<PatientOverview> filter(List<PatientOverview> all, PatientFilter f) {
        String q = Texts.searchKey(f.query());
        String qDigits = Texts.digitsOnly(f.query());
        boolean numericQuery = !qDigits.isEmpty() && !q.matches(".*\\p{L}.*");
        return all.stream().filter(o -> {
            Patient p = o.patient();
            if (f.inactiveOnly()) {
                if (p.active()) {
                    return false;
                }
            } else if (!f.includeInactive() && !p.active()) {
                return false;
            }
            if (!q.isEmpty()) {
                boolean byName = Texts.searchKey(p.fullName()).contains(q);
                boolean byDoc = numericQuery && p.document().contains(qDigits);
                if (!byName && !byDoc) {
                    return false;
                }
            }
            if (f.processId() != null && p.processId() != f.processId()) {
                return false;
            }
            if (f.cita() == PatientFilter.CitaFilter.WITH && !o.hasAppointment()) {
                return false;
            }
            if (f.cita() == PatientFilter.CitaFilter.WITHOUT && o.hasAppointment()) {
                return false;
            }
            WaStatus wa = o.waStatus();
            if (f.whatsApp() == PatientFilter.WhatsAppFilter.SIN_AVISAR && wa != WaStatus.NONE) {
                return false;
            }
            if (f.whatsApp() == PatientFilter.WhatsAppFilter.AVISADO && wa != WaStatus.AVISADO) {
                return false;
            }
            if (f.whatsApp() == PatientFilter.WhatsAppFilter.AGENDADO && wa != WaStatus.AGENDADO) {
                return false;
            }
            if (f.whatsApp() == PatientFilter.WhatsAppFilter.NO_RESPONDIO && wa != WaStatus.NO_RESPONDIO) {
                return false;
            }
            if (f.whatsApp() == PatientFilter.WhatsAppFilter.SIN_RESPUESTA
                    && wa != WaStatus.AVISADO && wa != WaStatus.NO_RESPONDIO) {
                return false;
            }
            LocalDate reg = p.registeredAt().toLocalDate();
            if (f.registeredFrom() != null && reg.isBefore(f.registeredFrom())) {
                return false;
            }
            return f.registeredTo() == null || !reg.isAfter(f.registeredTo());
        }).toList();
    }
}
