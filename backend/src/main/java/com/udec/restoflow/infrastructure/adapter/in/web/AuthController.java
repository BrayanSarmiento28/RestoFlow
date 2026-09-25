package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase;
import com.udec.restoflow.application.port.in.IniciarSesionUseCase;
import com.udec.restoflow.application.port.in.IniciarSesionUseCase.ComandoInicioSesion;
import com.udec.restoflow.application.port.in.RecuperarContrasenaUseCase;
import com.udec.restoflow.application.port.in.RecuperarContrasenaUseCase.ComandoRestablecer;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.LoginRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.LoginResponse;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.MensajeResponse;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.RecuperarRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.RestablecerRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.UsuarioResponse;
import com.udec.restoflow.infrastructure.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada: recibe las peticiones HTTP de Angular y llama a los casos de uso.
 * No contiene reglas de negocio; solo traduce JSON ↔ comandos del dominio.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String MENSAJE_RECUPERACION =
            "Si el correo está registrado, recibirás un código para restablecer tu contraseña.";

    private final IniciarSesionUseCase iniciarSesion;
    private final RecuperarContrasenaUseCase recuperarContrasena;
    private final GestionarCuentasUseCase cuentas;

    public AuthController(IniciarSesionUseCase iniciarSesion, RecuperarContrasenaUseCase recuperarContrasena,
                          GestionarCuentasUseCase cuentas) {
        this.iniciarSesion = iniciarSesion;
        this.recuperarContrasena = recuperarContrasena;
        this.cuentas = cuentas;
    }

    /** HU-01 · Iniciar sesión. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var resultado = iniciarSesion.iniciarSesion(new ComandoInicioSesion(request.email(), request.password()));
        return new LoginResponse(resultado.token(), resultado.expiraEnSegundos(),
                UsuarioResponse.desde(resultado.usuario()));
    }

    /** HU-02 · Paso 1: pedir el código. Siempre responde lo mismo para no revelar qué correos existen. */
    @PostMapping("/recuperar")
    public MensajeResponse recuperar(@Valid @RequestBody RecuperarRequest request) {
        recuperarContrasena.solicitarCodigo(request.email());
        return new MensajeResponse(MENSAJE_RECUPERACION);
    }

    /** HU-02 · Paso 2: usar el código para definir una contraseña nueva. */
    @PostMapping("/restablecer")
    public MensajeResponse restablecer(@Valid @RequestBody RestablecerRequest request) {
        recuperarContrasena.restablecerContrasena(
                new ComandoRestablecer(request.email(), request.codigo(), request.nuevaPassword()));
        return new MensajeResponse("Tu contraseña se actualizó correctamente. Ya puedes iniciar sesión.");
    }

    /** Datos del usuario dueño del token (el frontend lo usa al recargar la página). */
    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal UsuarioAutenticado actual) {
        return UsuarioResponse.desde(cuentas.obtenerCuenta(actual.id()));
    }
}
