package com.udec.restoflow.domain.ia;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Cuánto tiempo pasó entre dos horas del restaurante.
 * <p>
 * Las fechas de los pedidos se guardan como hora local ({@link LocalDateTime}) en la zona horaria del servidor
 * (variable {@code TZ}, America/Bogota). Antes de restarlas se ubican en esa zona: así un cambio de horario no
 * altera la cuenta si algún día el sistema se usa en un país que lo tenga (SonarQube, regla java:S8700).
 */
final class Duraciones {

    private Duraciones() {
    }

    static long minutosEntre(LocalDateTime desde, LocalDateTime hasta) {
        ZoneId zona = ZoneId.systemDefault();
        return Duration.between(desde.atZone(zona), hasta.atZone(zona)).toMinutes();
    }
}
