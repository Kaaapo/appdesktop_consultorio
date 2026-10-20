package co.sv.consultorio.db;

import java.util.List;

/**
 * Migraciones del esquema. Cada entrada es una versión; nunca se modifica una ya publicada,
 * siempre se agrega una nueva al final. La versión aplicada se guarda en {@code PRAGMA user_version}.
 */
final class Migrations {

    private static final List<String[]> VERSIONS = List.<String[]>of(
            // v1: esquema inicial
            new String[]{
                    """
                    CREATE TABLE admin (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        username TEXT NOT NULL,
                        password_hash TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )""",
                    """
                    CREATE TABLE process (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        months_to_second INTEGER NOT NULL CHECK (months_to_second >= 1),
                        message_template TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )""",
                    """
                    CREATE TABLE patient (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        first_name TEXT NOT NULL,
                        last_name TEXT NOT NULL,
                        document TEXT NOT NULL UNIQUE,
                        phone TEXT NOT NULL,
                        process_id INTEGER NOT NULL REFERENCES process(id),
                        first_appointment_date TEXT NOT NULL,
                        second_appointment_date TEXT NOT NULL,
                        registered_at TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1
                    )""",
                    "CREATE INDEX ix_patient_registered ON patient(registered_at DESC)",
                    "CREATE INDEX ix_patient_process ON patient(process_id)",
                    """
                    CREATE TABLE appointment (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patient_id INTEGER NOT NULL REFERENCES patient(id),
                        starts_at TEXT NOT NULL,
                        type TEXT NOT NULL CHECK (type IN ('PRIMERA', 'SEGUNDA', 'CONTROL')),
                        notified_at TEXT,
                        notified_text TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )""",
                    "CREATE INDEX ix_appointment_starts ON appointment(starts_at)",
                    "CREATE INDEX ix_appointment_patient ON appointment(patient_id)",
                    """
                    CREATE TABLE setting (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )"""
            },
            // v2: nombre completo, nacimiento, cita/control y estado de WhatsApp
            new String[]{
                    "ALTER TABLE patient ADD COLUMN full_name TEXT",
                    "ALTER TABLE patient ADD COLUMN birth_date TEXT",
                    "UPDATE patient SET full_name = trim(first_name || ' ' || last_name) WHERE full_name IS NULL OR full_name = ''",
                    "ALTER TABLE patient DROP COLUMN first_name",
                    "ALTER TABLE patient DROP COLUMN last_name",
                    "ALTER TABLE patient DROP COLUMN first_appointment_date",
                    "ALTER TABLE patient DROP COLUMN second_appointment_date",
                    """
                    CREATE TABLE appointment_v2 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patient_id INTEGER NOT NULL REFERENCES patient(id),
                        starts_at TEXT NOT NULL,
                        type TEXT NOT NULL CHECK (type IN ('CITA', 'CONTROL')),
                        notified_at TEXT,
                        notified_text TEXT,
                        wa_status TEXT NOT NULL DEFAULT 'NONE' CHECK (wa_status IN ('NONE', 'AVISADO', 'AGENDADO', 'NO_RESPONDIO')),
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )""",
                    """
                    INSERT INTO appointment_v2 (id, patient_id, starts_at, type, notified_at, notified_text, wa_status, created_at, updated_at)
                    SELECT id, patient_id, starts_at,
                           CASE WHEN type = 'CONTROL' THEN 'CONTROL' ELSE 'CITA' END,
                           notified_at, notified_text,
                           CASE WHEN notified_at IS NULL THEN 'NONE' ELSE 'AVISADO' END,
                           created_at, updated_at
                    FROM appointment""",
                    "DROP TABLE appointment",
                    "ALTER TABLE appointment_v2 RENAME TO appointment",
                    "CREATE INDEX ix_appointment_starts ON appointment(starts_at)",
                    "CREATE INDEX ix_appointment_patient ON appointment(patient_id)"
            },
            // v3: indicativo de país, aparte del teléfono local
            new String[]{
                    "ALTER TABLE patient ADD COLUMN country_code TEXT NOT NULL DEFAULT '57'"
            },
            // v4: el procedimiento puede quedar vacío (se asigna después, al editar)
            new String[]{
                    "PRAGMA foreign_keys = OFF",
                    """
                    CREATE TABLE patient_v4 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        full_name TEXT,
                        document TEXT NOT NULL UNIQUE,
                        phone TEXT NOT NULL,
                        country_code TEXT NOT NULL DEFAULT '57',
                        process_id INTEGER REFERENCES process(id),
                        birth_date TEXT,
                        registered_at TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1
                    )""",
                    """
                    INSERT INTO patient_v4 (id, full_name, document, phone, country_code, process_id, birth_date, registered_at, active)
                    SELECT id, full_name, document, phone, country_code, process_id, birth_date, registered_at, active
                    FROM patient""",
                    "DROP TABLE patient",
                    "ALTER TABLE patient_v4 RENAME TO patient",
                    "CREATE INDEX ix_patient_registered ON patient(registered_at DESC)",
                    "CREATE INDEX ix_patient_process ON patient(process_id)",
                    "PRAGMA foreign_keys = ON"
            }
    );

    private Migrations() {
    }

    static int latestVersion() {
        return VERSIONS.size();
    }

    static void apply(Database db) {
        int current = db.userVersion();
        for (int v = current; v < VERSIONS.size(); v++) {
            String[] statements = VERSIONS.get(v);
            int target = v + 1;
            boolean toggleFk = false;
            for (String sql : statements) {
                if (sql.strip().regionMatches(true, 0, "PRAGMA foreign_keys", 0, "PRAGMA foreign_keys".length())) {
                    toggleFk = true;
                    break;
                }
            }
            if (toggleFk) {
                db.execute("PRAGMA foreign_keys = OFF");
            }
            try {
                db.inTransaction(() -> {
                    for (String sql : statements) {
                        if (sql.strip().regionMatches(true, 0, "PRAGMA foreign_keys", 0, "PRAGMA foreign_keys".length())) {
                            continue;
                        }
                        db.execute(sql);
                    }
                    db.setUserVersion(target);
                });
            } finally {
                if (toggleFk) {
                    db.execute("PRAGMA foreign_keys = ON");
                }
            }
        }
    }
}
