package com.udec.restoflow.domain.exception;

/**
 * La operación es válida en general, pero no en el estado actual del registro.
 * Ejemplo: modificar un pedido que ya está En preparación. El API responde 409 (conflicto).
 */
public class OperacionNoPermitidaException extends DomainException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
