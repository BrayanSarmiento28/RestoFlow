package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.Usuario;

/**
 * HU-01 · Iniciar sesión.
 * Puerto de entrada: lo que el sistema ofrece. El controlador REST depende de esta interfaz.
 */
public interface IniciarSesionUseCase {

    ResultadoInicioSesion iniciarSesion(ComandoInicioSesion comando);

    /** Datos que envía el usuario. */
    record ComandoInicioSesion(String email, String contrasena) { }

    /** Lo que se devuelve si las credenciales son correctas. */
    record ResultadoInicioSesion(String token, long expiraEnSegundos, Usuario usuario) { }
}
