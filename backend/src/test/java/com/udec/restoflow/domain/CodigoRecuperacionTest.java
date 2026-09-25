package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.CodigoRecuperacionInvalidoException;
import com.udec.restoflow.domain.model.CodigoRecuperacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de las reglas del código de recuperación (HU-02). */
class CodigoRecuperacionTest {

    private final LocalDateTime ahora = LocalDateTime.of(2026, 9, 25, 10, 0);

    @Test
    @DisplayName("El código generado tiene exactamente 6 dígitos")
    void codigoDeSeisDigitos() {
        for (int i = 0; i < 100; i++) {
            String codigo = CodigoRecuperacion.generarCodigoPlano();
            assertEquals(6, codigo.length());
            assertTrue(codigo.chars().allMatch(Character::isDigit));
        }
    }

    @Test
    @DisplayName("El código vence a los 15 minutos")
    void venceALos15Minutos() {
        CodigoRecuperacion codigo = CodigoRecuperacion.nuevo(1L, "hash", ahora);
        assertTrue(codigo.estaVigente(ahora.plusMinutes(14)));
        assertFalse(codigo.estaVigente(ahora.plusMinutes(15)));
    }

    @Test
    @DisplayName("El código solo se puede usar una vez")
    void usoUnico() {
        CodigoRecuperacion codigo = CodigoRecuperacion.nuevo(1L, "hash", ahora);
        codigo.usar(ahora.plusMinutes(1));
        assertThrows(CodigoRecuperacionInvalidoException.class, () -> codigo.usar(ahora.plusMinutes(2)));
    }

    @Test
    @DisplayName("Se bloquea después de 5 intentos fallidos")
    void bloqueoPorIntentos() {
        CodigoRecuperacion codigo = CodigoRecuperacion.nuevo(1L, "hash", ahora);
        for (int i = 0; i < CodigoRecuperacion.MAXIMO_INTENTOS; i++) {
            codigo.registrarIntentoFallido();
        }
        assertFalse(codigo.estaVigente(ahora.plusMinutes(1)));
    }
}
