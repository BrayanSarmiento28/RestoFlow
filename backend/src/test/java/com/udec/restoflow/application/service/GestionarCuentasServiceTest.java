package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase.ComandoCrearCuenta;
import com.udec.restoflow.domain.exception.CorreoYaRegistradoException;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** HU-03 · Crear cuenta de empleado. */
class GestionarCuentasServiceTest {

    private Dobles.UsuariosEnMemoria usuarios;
    private GestionarCuentasService servicio;

    @BeforeEach
    void preparar() {
        usuarios = new Dobles.UsuariosEnMemoria();
        servicio = new GestionarCuentasService(usuarios, new Dobles.CifradoFalso(), new Dobles.RelojManual());
    }

    @Test
    @DisplayName("Crea la cuenta con la contraseña cifrada, nunca en texto plano")
    void creaCuentaConContrasenaCifrada() {
        Usuario creado = servicio.crearCuenta(
                new ComandoCrearCuenta("Laura Gómez", "laura@resto.com", "Mesero2026", Rol.MESERO));

        assertNotNull(creado.getId());
        assertEquals(Rol.MESERO, creado.getRol());
        assertNotEquals("Mesero2026", creado.getPasswordHash());
    }

    @Test
    @DisplayName("No permite dos cuentas con el mismo correo (sin importar mayúsculas)")
    void rechazaCorreoRepetido() {
        servicio.crearCuenta(new ComandoCrearCuenta("Laura Gómez", "laura@resto.com", "Mesero2026", Rol.MESERO));

        assertThrows(CorreoYaRegistradoException.class, () -> servicio.crearCuenta(
                new ComandoCrearCuenta("Otra Laura", "LAURA@resto.com", "Mesero2026", Rol.COCINA)));
        assertEquals(1, usuarios.contar());
    }

    @Test
    @DisplayName("Rechaza contraseñas débiles")
    void rechazaContrasenaDebil() {
        assertThrows(DomainException.class, () -> servicio.crearCuenta(
                new ComandoCrearCuenta("Laura Gómez", "laura@resto.com", "123", Rol.MESERO)));
        assertEquals(0, usuarios.contar());
    }
}
