package co.sv.consultorio.service;

import co.sv.consultorio.util.Fmt;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Fecha y hora actuales en America/Bogota (reemplazable en pruebas). */
public final class TimeSource {

    private final Clock clock;

    public TimeSource(Clock clock) {
        this.clock = clock.withZone(Fmt.ZONE);
    }

    public static TimeSource system() {
        return new TimeSource(Clock.system(Fmt.ZONE));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
