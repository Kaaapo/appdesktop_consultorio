package co.sv.consultorio.service;

import co.sv.consultorio.db.Database;
import co.sv.consultorio.repo.AdminRepository;
import co.sv.consultorio.repo.AppointmentRepository;
import co.sv.consultorio.repo.PatientRepository;
import co.sv.consultorio.repo.ProcessRepository;
import co.sv.consultorio.repo.SettingsRepository;

/** Punto de entrada del backend: crea los repositorios y servicios sobre una base de datos. */
public final class Services {

    private final Database db;
    private final TimeSource time;
    private final AuthService auth;
    private final SettingsService settings;
    private final ProcessService processes;
    private final PatientService patients;
    private final AppointmentService appointments;
    private final NotificationService notifications;
    private final DashboardService dashboard;
    private final ReportService reports;
    private final ImportService imports;

    public Services(Database db, TimeSource time) {
        this.db = db;
        this.time = time;
        ProcessRepository processRepo = new ProcessRepository(db);
        PatientRepository patientRepo = new PatientRepository(db);
        AppointmentRepository appointmentRepo = new AppointmentRepository(db);
        this.auth = new AuthService(new AdminRepository(db), time);
        this.settings = new SettingsService(new SettingsRepository(db));
        this.processes = new ProcessService(processRepo, time);
        this.patients = new PatientService(patientRepo, processRepo, appointmentRepo, time);
        this.appointments = new AppointmentService(db, appointmentRepo, patientRepo, processRepo, settings, time);
        this.notifications = new NotificationService(appointmentRepo, time);
        this.dashboard = new DashboardService(appointments, patients, settings, time);
        this.reports = new ReportService(patients, processes, appointments, time);
        this.imports = new ImportService(db, patients, patientRepo, processes, time);
    }

    public Database db() {
        return db;
    }

    public TimeSource time() {
        return time;
    }

    public AuthService auth() {
        return auth;
    }

    public SettingsService settings() {
        return settings;
    }

    public ProcessService processes() {
        return processes;
    }

    public PatientService patients() {
        return patients;
    }

    public AppointmentService appointments() {
        return appointments;
    }

    public NotificationService notifications() {
        return notifications;
    }

    public DashboardService dashboard() {
        return dashboard;
    }

    public ReportService reports() {
        return reports;
    }

    public ImportService imports() {
        return imports;
    }
}
