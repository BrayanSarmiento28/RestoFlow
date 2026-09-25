package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase.ComandoCrearCuenta;
import com.udec.restoflow.application.port.in.IniciarSesionUseCase.ComandoInicioSesion;
import com.udec.restoflow.application.port.in.IniciarSesionUseCase.ResultadoInicioSesion;
import com.udec.restoflow.domain.exception.CredencialesInvalidasException;
import com.udec.restoflow.domain.exception.CuentaInactivaException;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** HU-01 · Iniciar sesión. */
class IniciarSesionServiceTest {

    private Dobles.UsuariosEnMemoria usuarios;
    private IniciarSesionService servicio;
    private Usuario laura;

    @BeforeEach
    void preparar() {
        usuarios = new Dobles.UsuariosEnMemoria();
        Dobles.CifradoFalso cifrado = new Dobles.CifradoFalso();
        laura = new GestionarCuentasService(usuarios, cifrado, new Dobles.RelojManual())
                .crearCuenta(new ComandoCrearCuenta("Laura Gómez", "laura@resto.com", "Mesero2026", Rol.MESERO));
        servicio = new IniciarSesionService(usuarios, cifrado, new Dobles.TokenFalso());
    }

    @Test
    @DisplayName("Con credenciales correctas entrega el token y los datos del usuario")
    void loginCorrecto() {
        ResultadoInicioSesion resultado = servicio.iniciarSesion(new ComandoInicioSesion("laura@resto.com", "Mesero2026"));

        assertEquals("token-de-laura@resto.com", resultado.token());
        assertEquals(Rol.MESERO, resultado.usuario().getRol());
    }

    @Test
    @DisplayName("El correo no distingue mayúsculas")
    void correoSinDistinguirMayusculas() {
        assertEquals(laura.getId(),
                servicio.iniciarSesion(new ComandoInicioSesion("LAURA@Resto.com", "Mesero2026")).usuario().getId());
    }

    @Test
    @DisplayName("Contraseña incorrecta o correo inexistente dan el MISMO error (no se dan pistas)")
    void mismoErrorParaAmbosCasos() {
        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.iniciarSesion(new ComandoInicioSesion("laura@resto.com", "equivocada")));
        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.iniciarSesion(new ComandoInicioSesion("nadie@resto.com", "Mesero2026")));
        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.iniciarSesion(new ComandoInicioSesion("no-es-un-correo", "Mesero2026")));
    }

    @Test
    @DisplayName("Una cuenta desactivada no puede ingresar aunque la contraseña sea correcta")
    void cuentaInactiva() {
        laura.desactivar();
        usuarios.guardar(laura);

        assertThrows(CuentaInactivaException.class,
                () -> servicio.iniciarSesion(new ComandoInicioSesion("laura@resto.com", "Mesero2026")));
    }
}
