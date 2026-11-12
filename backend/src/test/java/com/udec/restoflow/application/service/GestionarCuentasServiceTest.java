package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase.ComandoCrearCuenta;
import com.udec.restoflow.domain.exception.CorreoYaRegistradoException;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HU-03 · Crear, desactivar y reactivar cuentas del personal. */
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

    @Test
    @DisplayName("Desactiva y reactiva la cuenta de otra persona; la cuenta no se borra")
    void desactivaYReactiva() {
        Usuario admin = servicio.crearCuenta(new ComandoCrearCuenta("Ana Admin", "ana@resto.com", "Admin2026", Rol.ADMIN));
        Usuario laura = servicio.crearCuenta(new ComandoCrearCuenta("Laura Gómez", "laura@resto.com", "Mesero2026", Rol.MESERO));

        assertFalse(servicio.cambiarEstado(laura.getId(), false, admin.getId()).isActivo());
        assertFalse(usuarios.buscarPorId(laura.getId()).orElseThrow().isActivo());
        assertEquals(2, usuarios.contar());

        assertTrue(servicio.cambiarEstado(laura.getId(), true, admin.getId()).isActivo());
        // Repetir el mismo estado no falla: simplemente no cambia nada
        assertTrue(servicio.cambiarEstado(laura.getId(), true, admin.getId()).isActivo());
    }

    @Test
    @DisplayName("El administrador no puede desactivar su propia cuenta (siempre queda uno con acceso)")
    void noSeDesactivaASiMismo() {
        Usuario admin = servicio.crearCuenta(new ComandoCrearCuenta("Ana Admin", "ana@resto.com", "Admin2026", Rol.ADMIN));

        Long adminId = admin.getId();
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.cambiarEstado(adminId, false, adminId));
        assertTrue(usuarios.buscarPorId(admin.getId()).orElseThrow().isActivo());
    }

    @Test
    @DisplayName("Cambiar el estado de una cuenta que no existe responde 'no encontrado'")
    void cuentaInexistente() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.cambiarEstado(99L, false, 1L));
    }
}
