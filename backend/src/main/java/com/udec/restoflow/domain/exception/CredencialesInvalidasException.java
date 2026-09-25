package com.udec.restoflow.domain.exception;

/**
 * El correo o la contraseña no son correctos.
 * Por seguridad el mensaje no indica cuál de los dos falló.
 */
public class CredencialesInvalidasException extends DomainException {

    public CredencialesInvalidasException() {
        super("Correo o contraseña incorrectos");
    }
}
