package com.udec.restoflow.domain.exception;

/** El código para restablecer la contraseña no existe, ya se usó o está vencido. */
public class CodigoRecuperacionInvalidoException extends DomainException {

    public CodigoRecuperacionInvalidoException() {
        super("El código no es válido o ya venció. Solicite uno nuevo");
    }
}
