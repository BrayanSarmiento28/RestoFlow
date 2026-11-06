package com.udec.restoflow.application.service;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Mi cuenta · Cambiar la contraseña con la sesión iniciada. */
class CambiarContrasenaServiceTest {

    private Dobles.UsuariosEnMemoria usuarios;
    private CambiarContrasenaService servicio;
    private Long id;

    @BeforeEach
    void preparar() {
        usuarios = new Dobles.UsuariosEnMemoria();
        Dobles.CifradoFalso cifrado = new Dobles.CifradoFalso();
        servicio = new CambiarContrasenaService(usuarios, cifrado);
        id = usuarios.guardar(Usuario.nuevo("Laura Gómez", "laura@resto.com", cifrado.cifrar("Mesero2026"), Rol.MESERO,
                LocalDateTime.now())).getId();
    }

    private String hashGuardado() {
        return usuarios.buscarPorId(id).orElseThrow().getPasswordHash();
    }

    @Test
    @DisplayName("Con la contraseña actual correcta, guarda la nueva cifrada")
    void cambia() {
        servicio.cambiar(id, "Mesero2026", "Nueva2027");
        assertEquals("cifrado:Nueva2027", hashGuardado());
    }

    @Test
    @DisplayName("Si la actual no es correcta, no cambia nada (400, no 401: no se cierra la sesión)")
    void actualIncorrecta() {
        DomainException e = assertThrows(DomainException.class, () -> servicio.cambiar(id, "Otra2026", "Nueva2027"));
        assertEquals("La contraseña actual no es correcta", e.getMessage());
        assertThrows(DomainException.class, () -> servicio.cambiar(id, null, "Nueva2027"));
        assertEquals("cifrado:Mesero2026", hashGuardado());
    }

    @Test
    @DisplayName("La nueva debe ser segura (8 caracteres, letras y números) y distinta de la actual")
    void reglasDeLaNueva() {
        assertThrows(DomainException.class, () -> servicio.cambiar(id, "Mesero2026", "corta1"));
        assertThrows(DomainException.class, () -> servicio.cambiar(id, "Mesero2026", "soloLetrasAqui"));
        DomainException igual = assertThrows(DomainException.class,
                () -> servicio.cambiar(id, "Mesero2026", "Mesero2026"));
        assertEquals("La nueva contraseña debe ser distinta de la actual", igual.getMessage());
        assertEquals("cifrado:Mesero2026", hashGuardado());
    }

    @Test
    @DisplayName("Un usuario que ya no existe responde 'no encontrado'")
    void usuarioInexistente() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.cambiar(999L, "Mesero2026", "Nueva2027"));
    }
}
