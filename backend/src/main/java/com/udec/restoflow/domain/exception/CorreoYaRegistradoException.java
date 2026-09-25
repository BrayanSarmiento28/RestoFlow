package com.udec.restoflow.domain.exception;

/** Ya existe una cuenta con ese correo electrónico. */
public class CorreoYaRegistradoException extends DomainException {

    public CorreoYaRegistradoException(String email) {
        super("Ya existe una cuenta registrada con el correo " + email);
    }
}
