package com.udec.restoflow.application.port.in;

/**
 * HU-02 · Recuperar contraseña. Tiene dos pasos:
 * <ol>
 *   <li>El usuario escribe su correo y recibe un código de 6 dígitos.</li>
 *   <li>El usuario escribe el código y su nueva contraseña.</li>
 * </ol>
 */
public interface RecuperarContrasenaUseCase {

    /** Paso 1: genera un código y lo envía al correo (si la cuenta existe). */
    void solicitarCodigo(String email);

    /** Paso 2: valida el código y cambia la contraseña. */
    void restablecerContrasena(ComandoRestablecer comando);

    record ComandoRestablecer(String email, String codigo, String nuevaContrasena) { }
}
