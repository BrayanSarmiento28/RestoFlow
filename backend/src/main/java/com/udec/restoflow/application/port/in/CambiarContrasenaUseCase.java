package com.udec.restoflow.application.port.in;

/**
 * Mi cuenta · Cada usuario cambia su propia contraseña escribiendo la actual (así, si alguien encuentra la sesión
 * abierta en un computador, no puede cambiarla sin saberla).
 */
public interface CambiarContrasenaUseCase {

    /** Cambia la contraseña del usuario de la sesión. La nueva debe ser segura y distinta de la actual. */
    void cambiar(Long usuarioId, String actual, String nueva);
}
