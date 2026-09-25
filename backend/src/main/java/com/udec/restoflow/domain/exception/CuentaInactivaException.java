package com.udec.restoflow.domain.exception;

/** La cuenta existe pero fue desactivada por el administrador. */
public class CuentaInactivaException extends DomainException {

    public CuentaInactivaException() {
        super("La cuenta está desactivada. Comuníquese con el administrador");
    }
}
