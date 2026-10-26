package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.CuentaInactivaException;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de las reglas del dominio Usuario (sin Spring ni base de datos). */
class UsuarioTest {

    private Usuario usuarioValido() {
        return Usuario.nuevo("Laura Gómez", "laura@resto.com", "hash", Rol.MESERO, LocalDateTime.now());
    }

    @Test
    @DisplayName("Una cuenta nueva queda activa")
    void cuentaNuevaQuedaActiva() {
        assertTrue(usuarioValido().isActivo());
    }

    @Test
    @DisplayName("El correo se guarda en minúsculas y sin espacios")
    void normalizaElCorreo() {
        Usuario u = Usuario.nuevo("Laura Gómez", "  Laura@Resto.COM ", "hash", Rol.MESERO, LocalDateTime.now());
        assertEquals("laura@resto.com", u.getEmail());
    }

    @Test
    @DisplayName("Rechaza un correo con formato inválido")
    void rechazaCorreoInvalido() {
        assertThrows(DomainException.class, () -> Usuario.normalizarEmail("laura@resto"));
    }

    @Test
    @DisplayName("Rechaza nombres de menos de 3 caracteres")
    void rechazaNombreCorto() {
        assertThrows(DomainException.class,
                () -> Usuario.nuevo("Lu", "lu@resto.com", "hash", Rol.MESERO, LocalDateTime.now()));
    }

    @Test
    @DisplayName("HU-01: una cuenta desactivada no puede ingresar")
    void cuentaInactivaNoIngresa() {
        Usuario u = usuarioValido();
        u.desactivar();
        assertThrows(CuentaInactivaException.class, u::validarPuedeIngresar);
    }

    @Test
    @DisplayName("Contraseña segura: mínimo 8 caracteres, con letras y números")
    void politicaDeContrasenas() {
        assertThrows(DomainException.class, () -> Usuario.validarContrasenaSegura("Ab1"));
        assertThrows(DomainException.class, () -> Usuario.validarContrasenaSegura("solamenteletras"));
        assertThrows(DomainException.class, () -> Usuario.validarContrasenaSegura("12345678"));
        assertDoesNotThrow(() -> Usuario.validarContrasenaSegura("Resto2026"));
    }

    @ParameterizedTest(name = "\"{0}\" es válido")
    @ValueSource(strings = {"laura@resto.com", "  LAURA.Gomez+cocina@Resto.com.co ", "a_b-c@mi-restaurante.co", "x@a.b"})
    @DisplayName("Correos válidos: una @ y un dominio con punto (se guardan en minúsculas y sin espacios)")
    void correosValidos(String correo) {
        assertEquals(correo.trim().toLowerCase(), Usuario.normalizarEmail(correo));
    }

    @ParameterizedTest(name = "\"{0}\" no es válido")
    @ValueSource(strings = {"laura", "@resto.com", "laura@", "laura@@resto.com", "laura@resto.com@x.co", "laura@.resto.com",
            "laura@resto.com.", "laura@resto..com", "lau ra@resto.com", "laura@res*to.com", "laura@resto"})
    @DisplayName("Correos inválidos: sin @, con dos @, dominio sin punto o con partes vacías, o caracteres no permitidos")
    void correosInvalidos(String correo) {
        assertThrows(DomainException.class, () -> Usuario.normalizarEmail(correo));
    }

    @Test
    @DisplayName("Un correo enorme se rechaza rápido (la validación no se queda pensando: sin riesgo de ReDoS)")
    void correoEnormeRapido() {
        String ataque = "a".repeat(50_000) + "@" + "b.".repeat(25_000) + "!";
        long inicio = System.nanoTime();
        assertThrows(DomainException.class, () -> Usuario.normalizarEmail(ataque));
        assertTrue((System.nanoTime() - inicio) / 1_000_000 < 1000, "la validación debe tardar menos de 1 segundo");
    }
}
