package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase.ComandoCrearCuenta;
import com.udec.restoflow.application.port.in.IniciarSesionUseCase.ComandoInicioSesion;
import com.udec.restoflow.application.port.in.RecuperarContrasenaUseCase.ComandoRestablecer;
import com.udec.restoflow.domain.exception.CodigoRecuperacionInvalidoException;
import com.udec.restoflow.domain.model.CodigoRecuperacion;
import com.udec.restoflow.domain.model.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** HU-02 · Recuperar contraseña con código de 6 dígitos. */
class RecuperarContrasenaServiceTest {

    private static final String EMAIL = "laura@resto.com";

    private Dobles.UsuariosEnMemoria usuarios;
    private Dobles.CifradoFalso cifrado;
    private Dobles.CorreoFalso correo;
    private Dobles.RelojManual reloj;
    private RecuperarContrasenaService servicio;

    @BeforeEach
    void preparar() {
        usuarios = new Dobles.UsuariosEnMemoria();
        cifrado = new Dobles.CifradoFalso();
        correo = new Dobles.CorreoFalso();
        reloj = new Dobles.RelojManual();
        new GestionarCuentasService(usuarios, cifrado, reloj)
                .crearCuenta(new ComandoCrearCuenta("Laura Gómez", EMAIL, "Mesero2026", Rol.MESERO));
        servicio = new RecuperarContrasenaService(usuarios, new Dobles.CodigosEnMemoria(), cifrado, correo, reloj);
    }

    private void restablecer(String codigo, String nueva) {
        servicio.restablecerContrasena(new ComandoRestablecer(EMAIL, codigo, nueva));
    }

    @Test
    @DisplayName("Flujo completo: pide el código, lo usa y entra con la contraseña nueva")
    void flujoCompleto() {
        servicio.solicitarCodigo(EMAIL);
        assertEquals(EMAIL, correo.ultimoDestinatario);

        restablecer(correo.ultimoCodigo, "NuevaClave2026");

        IniciarSesionService login = new IniciarSesionService(usuarios, cifrado, new Dobles.TokenFalso());
        assertDoesNotThrow(() -> login.iniciarSesion(new ComandoInicioSesion(EMAIL, "NuevaClave2026")));
    }

    @Test
    @DisplayName("Si el correo no existe no envía nada ni lanza error (no revela qué correos existen)")
    void correoInexistente() {
        assertDoesNotThrow(() -> servicio.solicitarCodigo("nadie@resto.com"));
        assertEquals(0, correo.enviados);
    }

    @Test
    @DisplayName("El código vencido (más de 15 minutos) no sirve")
    void codigoVencido() {
        servicio.solicitarCodigo(EMAIL);
        reloj.avanzarMinutos(CodigoRecuperacion.VIGENCIA_MINUTOS + 1);

        assertThrows(CodigoRecuperacionInvalidoException.class, () -> restablecer(correo.ultimoCodigo, "NuevaClave2026"));
    }

    @Test
    @DisplayName("El código solo se puede usar una vez")
    void codigoDeUnSoloUso() {
        servicio.solicitarCodigo(EMAIL);
        String codigo = correo.ultimoCodigo;
        restablecer(codigo, "NuevaClave2026");

        assertThrows(CodigoRecuperacionInvalidoException.class, () -> restablecer(codigo, "OtraClave2026"));
    }

    @Test
    @DisplayName("Al pedir un código nuevo, el anterior deja de servir")
    void soloSirveElUltimoCodigo() {
        servicio.solicitarCodigo(EMAIL);
        String primero = correo.ultimoCodigo;
        servicio.solicitarCodigo(EMAIL);

        if (!primero.equals(correo.ultimoCodigo)) {
            assertThrows(CodigoRecuperacionInvalidoException.class, () -> restablecer(primero, "NuevaClave2026"));
        }
        assertDoesNotThrow(() -> restablecer(correo.ultimoCodigo, "NuevaClave2026"));
    }

    @Test
    @DisplayName("Tras 5 intentos fallidos el código se bloquea, aunque después se escriba bien")
    void bloqueoTrasIntentosFallidos() {
        servicio.solicitarCodigo(EMAIL);
        String correcto = correo.ultimoCodigo;
        String incorrecto = correcto.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < CodigoRecuperacion.MAXIMO_INTENTOS; i++) {
            assertThrows(CodigoRecuperacionInvalidoException.class, () -> restablecer(incorrecto, "NuevaClave2026"));
        }
        assertThrows(CodigoRecuperacionInvalidoException.class, () -> restablecer(correcto, "NuevaClave2026"));
    }
}
