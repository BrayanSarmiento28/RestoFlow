package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.infrastructure.config.HistorialDemoConfig.RelojAjustable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RelojAjustableTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDateTime MEDIODIA = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Test
    @DisplayName("Se mueve a la fecha indicada y avanza de a minutos")
    void fijaYAvanza() {
        RelojAjustable reloj = new RelojAjustable(BOGOTA);
        reloj.fijar(MEDIODIA);
        reloj.avanzarMinutos(30);

        assertThat(LocalDateTime.now(reloj)).isEqualTo(MEDIODIA.plusMinutes(30));
        assertThat(reloj.getZone()).isEqualTo(BOGOTA);
        assertThat(reloj.withZone(ZoneId.of("UTC"))).isSameAs(reloj);
    }

    @Test
    @DisplayName("Dos relojes son iguales si marcan el mismo instante en la misma zona")
    void igualdad() {
        RelojAjustable uno = new RelojAjustable(BOGOTA);
        RelojAjustable otro = new RelojAjustable(BOGOTA);
        uno.fijar(MEDIODIA);
        otro.fijar(MEDIODIA);

        assertThat(uno).isEqualTo(otro).hasSameHashCodeAs(otro);

        otro.avanzarMinutos(1);
        assertThat(uno).isNotEqualTo(otro).isNotEqualTo(Clock.systemUTC());   // otro tipo de reloj
        RelojAjustable utc = new RelojAjustable(ZoneId.of("UTC"));
        utc.fijar(MEDIODIA);
        assertThat(uno).isNotEqualTo(utc);
    }
}
