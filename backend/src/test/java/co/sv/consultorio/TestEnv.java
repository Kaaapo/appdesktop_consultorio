package co.sv.consultorio;

import co.sv.consultorio.db.Database;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.service.PatientInput;
import co.sv.consultorio.service.Services;
import co.sv.consultorio.service.TimeSource;
import co.sv.consultorio.util.Fmt;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Base de datos en memoria con un reloj controlable. "Hoy" = martes 13 de octubre de 2026, 1:00 p. m. */
public final class TestEnv implements AutoCloseable {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 13, 0);
    public static final LocalDate TODAY = NOW.toLocalDate();

    public final MutableClock clock = new MutableClock(NOW);
    public final Database db = Database.openInMemory();
    public final Services s = new Services(db, new TimeSource(clock));

    public Process process(String name, int months) {
        return s.processes().create(name, months, "Hola {nombre}, tu cita de {proceso} es el {fecha}.");
    }

    public Patient patient(String first, String last, String doc, Process p, LocalDate birthDate) {
        LocalDate birth = birthDate != null && birthDate.isBefore(TODAY) ? birthDate : TODAY.minusYears(30);
        return s.patients().create(new PatientInput((first + " " + last).trim(), doc, "3105550142", p.id(), birth));
    }

    @Override
    public void close() {
        db.close();
    }

    public static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(LocalDateTime now) {
            set(now);
        }

        public void set(LocalDateTime now) {
            instant = now.atZone(Fmt.ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return Fmt.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
