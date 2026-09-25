package com.udec.restoflow.domain.exception;

/**
 * Error base del negocio: se lanza cuando se incumple una regla del dominio.
 * Todas las excepciones del dominio heredan de esta clase.
 */
public class DomainException extends RuntimeException {

    public DomainException(String mensaje) {
        super(mensaje);
    }
}
