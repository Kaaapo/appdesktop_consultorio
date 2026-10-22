package co.sv.consultorio.service;

import co.sv.consultorio.db.Database;
import co.sv.consultorio.domain.AppSettings;
import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.WaStatus;
import co.sv.consultorio.repo.AppointmentRepository;
import co.sv.consultorio.repo.PatientRepository;
import co.sv.consultorio.repo.ProcessRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AppointmentService {

    public static final int GAP_MINUTES = AppointmentRepository.GAP_MINUTES;

    /** Hasta cuántos días adelante se buscan espacios libres. */
    static final int SEARCH_DAYS = 120;

    public enum SlotState { FREE, BUSY, PAST }

    public record Slot(LocalTime time, SlotState state, Appointment occupant) {
        public boolean free() {
            return state == SlotState.FREE;
        }
    }

    /**
     * Resultado de asignar o reasignar. Guarda el estado anterior para poder deshacer.
     *
     * @param previous cita antes de reasignar (null si era una cita nueva)
     */
    public record AssignResult(Appointment saved, Appointment previous, Patient patientBefore) {
        public boolean rescheduled() {
            return previous != null;
        }
    }

    private final Database db;
    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final ProcessRepository processes;
    private final SettingsService settings;
    private final TimeSource time;

    public AppointmentService(Database db, AppointmentRepository appointments, PatientRepository patients,
                              ProcessRepository processes, SettingsService settings, TimeSource time) {
        this.db = db;
        this.appointments = appointments;
        this.patients = patients;
        this.processes = processes;
        this.settings = settings;
        this.time = time;
    }

    public Appointment get(long id) {
        return appointments.findById(id);
    }

    public AppointmentView view(long appointmentId) {
        Appointment a = appointments.findById(appointmentId);
        if (a == null) {
            return null;
        }
        List<AppointmentView> one = views(List.of(a));
        return one.isEmpty() ? null : one.get(0);
    }

    /** Cita vigente del paciente (desde hoy), o null. */
    public Appointment current(long patientId) {
        return appointments.findCurrent(patientId, time.today());
    }

    /**
     * Crea una cita. No mueve las que el paciente ya tiene: para eso está {@link #reschedule}.
     */
    public AssignResult assign(long patientId, AppointmentType type, LocalDateTime startsAt) {
        return add(patientId, type, startsAt);
    }

    public AssignResult assign(long patientId, AppointmentType type, LocalDateTime startsAt, Long processId) {
        return add(patientId, type, startsAt, processId);
    }

    public void cancel(long appointmentId) {
        if (appointments.findById(appointmentId) == null) {
            throw new BusinessException("La cita ya no existe.");
        }
        appointments.delete(appointmentId);
    }

    /** Mueve una cita concreta. Borra el aviso para poder avisar de la nueva fecha. */
    public AssignResult reschedule(long appointmentId, AppointmentType type, LocalDateTime startsAt) {
        return reschedule(appointmentId, type, startsAt, null);
    }

    public AssignResult reschedule(long appointmentId, AppointmentType type, LocalDateTime startsAt, Long processId) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(startsAt, "startsAt");
        return db.inTransaction(() -> {
            Appointment current = appointments.findById(appointmentId);
            if (current == null) {
                throw new BusinessException("La cita ya no existe.");
            }
            Patient p = patients.findById(current.patientId());
            if (p == null || !p.active()) {
                throw new BusinessException("El paciente no existe o está inactivo.");
            }
            LocalDateTime now = time.now();
            if (startsAt.isBefore(now)) {
                throw new BusinessException("Esa hora ya pasó. Elige una fecha y hora futuras.");
            }
            if (appointments.slotTaken(startsAt, current.id())) {
                throw new BusinessException("Esa hora queda a menos de hora y media de otra cita. Elige otra.");
            }
            Long process = resolveProcess(current.patientId(), processId != null ? processId : current.processId());
            appointments.update(new Appointment(current.id(), current.patientId(), startsAt, type, null, null,
                    WaStatus.NONE, current.createdAt(), process), now);
            return new AssignResult(appointments.findById(current.id()), current, p);
        });
    }

    /** Crea otra cita o control sin reemplazar las que ya tiene. */
    public AssignResult add(long patientId, AppointmentType type, LocalDateTime startsAt) {
        return save(patientId, type, startsAt, null);
    }

    public AssignResult add(long patientId, AppointmentType type, LocalDateTime startsAt, Long processId) {
        return save(patientId, type, startsAt, processId);
    }

    private AssignResult save(long patientId, AppointmentType type, LocalDateTime startsAt, Long processId) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(startsAt, "startsAt");
        return db.inTransaction(() -> {
            Patient p = patients.findById(patientId);
            if (p == null || !p.active()) {
                throw new BusinessException("El paciente no existe o está inactivo.");
            }
            LocalDateTime now = time.now();
            if (startsAt.isBefore(now)) {
                throw new BusinessException("Esa hora ya pasó. Elige una fecha y hora futuras.");
            }
            if (appointments.slotTaken(startsAt, 0)) {
                throw new BusinessException("Esa hora queda a menos de hora y media de otra cita. Elige otra.");
            }
            Long process = resolveProcess(patientId, processId);
            long id = appointments.insert(patientId, startsAt, type, now, process);
            return new AssignResult(appointments.findById(id), null, p);
        });
    }

    /** Usa el procedimiento elegido, o el único del paciente si no eligieron. */
    private Long resolveProcess(long patientId, Long requested) {
        if (requested != null) {
            if (processes.findById(requested) == null) {
                throw new BusinessException("El procedimiento ya no existe.");
            }
            patients.linkProcedure(patientId, requested);
            return requested;
        }
        List<Long> ids = patients.procedureIds(patientId);
        if (ids.size() == 1) {
            return ids.get(0);
        }
        Patient patient = patients.findById(patientId);
        if (ids.isEmpty() && patient != null && patient.processId() != null) {
            return patient.processId();
        }
        throw new BusinessException("Elige el procedimiento de la cita.");
    }

    /** Deshace una asignación o reasignación recién hecha. */
    public void undo(AssignResult r) {
        db.inTransaction(() -> {
            if (r.previous() == null) {
                appointments.delete(r.saved().id());
            } else {
                appointments.update(r.previous(), time.now());
            }
        });
    }

    public List<Slot> daySlots(LocalDate date) {
        return daySlots(date, 0);
    }

    /**
     * Horas del día. Si se está reasignando una cita, {@code excludeAppointmentId} no bloquea
     * las horas vecinas: solo deja desactivada la hora en la que ya está esa cita.
     */
    public List<Slot> daySlots(LocalDate date, long excludeAppointmentId) {
        LocalDateTime now = time.now();
        List<Appointment> day = appointments.findBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        List<Slot> out = new ArrayList<>();
        for (LocalTime t : settings.slotsFor(date.getDayOfWeek())) {
            LocalDateTime at = date.atTime(t);
            Appointment own = ownAt(day, excludeAppointmentId, at);
            Appointment occ = nearestWithinGap(day, at, excludeAppointmentId);
            SlotState st = occ != null || own != null ? SlotState.BUSY
                    : at.isBefore(now) ? SlotState.PAST : SlotState.FREE;
            out.add(new Slot(t, st, occ != null ? occ : own));
        }
        return out;
    }

    private static Appointment ownAt(List<Appointment> day, long excludeAppointmentId, LocalDateTime at) {
        if (excludeAppointmentId == 0) {
            return null;
        }
        for (Appointment a : day) {
            if (a.id() == excludeAppointmentId && a.startsAt().equals(at)) {
                return a;
            }
        }
        return null;
    }

    /** Cita del día cuyo inicio está a menos de hora y media de {@code at}, salvo la que se está moviendo. */
    private static Appointment nearestWithinGap(List<Appointment> day, LocalDateTime at, long excludeAppointmentId) {
        Appointment nearest = null;
        long best = GAP_MINUTES;
        for (Appointment a : day) {
            if (a.id() == excludeAppointmentId) {
                continue;
            }
            long minutes = Math.abs(java.time.Duration.between(a.startsAt(), at).toMinutes());
            if (minutes < GAP_MINUTES && minutes < best) {
                best = minutes;
                nearest = a;
            }
        }
        return nearest;
    }

    /** Primeros {@code n} días laborales (desde hoy) con su primera hora libre. */
    public List<LocalDateTime> firstFreeSlots(int n) {
        AppSettings s = settings.get();
        List<LocalDateTime> out = new ArrayList<>();
        LocalDate d = time.today();
        for (int i = 0; i < SEARCH_DAYS && out.size() < n; i++, d = d.plusDays(1)) {
            if (!s.workDays().contains(d.getDayOfWeek())) {
                continue;
            }
            LocalDate day = d;
            daySlots(day).stream().filter(Slot::free).findFirst().ifPresent(slot -> out.add(day.atTime(slot.time())));
        }
        return out;
    }

    /** Primera fecha laboral con espacio libre a partir de {@code from}. */
    public LocalDate firstFreeDayFrom(LocalDate from) {
        AppSettings s = settings.get();
        LocalDate d = from.isBefore(time.today()) ? time.today() : from;
        for (int i = 0; i < SEARCH_DAYS; i++, d = d.plusDays(1)) {
            if (s.workDays().contains(d.getDayOfWeek()) && daySlots(d).stream().anyMatch(Slot::free)) {
                return d;
            }
        }
        return from;
    }

    /** Número de citas por día del mes. */
    public Map<LocalDate, Integer> monthCounts(YearMonth month) {
        Map<LocalDate, Integer> out = new TreeMap<>();
        for (Appointment a : appointments.findBetween(month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay())) {
            out.merge(a.startsAt().toLocalDate(), 1, Integer::sum);
        }
        return out;
    }

    /** Primera fecha libre para la siguiente cita, según los meses del procedimiento principal. */
    public LocalDate suggestedDay(PatientOverview overview, LocalDate today) {
        return suggestedDay(overview, overview == null ? null : overview.process(), today);
    }

    /** Igual, pero con el procedimiento elegido en la cita. */
    public LocalDate suggestedDay(PatientOverview overview, Process process, LocalDate today) {
        if (overview == null || process == null || today == null) {
            return null;
        }
        LocalDate last = null;
        for (AppointmentView visit : forPatient(overview.patient().id())) {
            if (visit.appointment().type() != AppointmentType.CITA || visit.process() == null
                    || visit.process().id() != process.id()) {
                continue;
            }
            LocalDate day = visit.appointment().startsAt().toLocalDate();
            if (last == null || day.isAfter(last)) {
                last = day;
            }
        }
        LocalDate base = last == null ? today : last.plusMonths(process.monthsToSecond());
        if (base.isBefore(today)) {
            base = today;
        }
        return firstFreeDayFrom(base);
    }

    /** Citas de pacientes activos entre dos fechas (ambas incluidas), en orden. */
    public List<AppointmentView> between(LocalDate from, LocalDate toInclusive) {
        return views(appointments.findBetween(from.atStartOfDay(), toInclusive.plusDays(1).atStartOfDay()));
    }

    public List<AppointmentView> forPatient(long patientId) {
        return views(appointments.findByPatient(patientId));
    }

    /** Todas las citas, agrupadas por paciente y en orden de fecha. */
    public Map<Long, List<AppointmentView>> byPatient() {
        return views(appointments.findAllOrdered()).stream()
                .collect(Collectors.groupingBy(v -> v.appointment().patientId(),
                        LinkedHashMap::new, Collectors.toList()));
    }

    List<AppointmentView> views(List<Appointment> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Process> procs = processes.findAll().stream().collect(Collectors.toMap(Process::id, Function.identity()));
        Map<Long, Patient> pats = patients.findAll().stream().collect(Collectors.toMap(Patient::id, Function.identity()));
        Map<Long, Integer> numbers = appointments.citaNumberByAppointment();
        List<AppointmentView> out = new ArrayList<>(list.size());
        for (Appointment a : list) {
            Patient p = pats.get(a.patientId());
            Long processId = a.processId() != null ? a.processId() : p == null ? null : p.processId();
            out.add(new AppointmentView(a, p, processId == null ? null : procs.get(processId),
                    numbers.getOrDefault(a.id(), 0)));
        }
        return out;
    }
}
