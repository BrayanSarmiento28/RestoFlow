package com.udec.restoflow.domain.exception;

/**
 * Un servicio de terceros (por ejemplo Open Food Facts) no respondió o respondió con error.
 * El API lo devuelve como 503: no es culpa del usuario y puede intentarlo más tarde.
 */
public class ServicioExternoNoDisponibleException extends RuntimeException {

    public ServicioExternoNoDisponibleException(String servicio) {
        super(servicio + " no está disponible en este momento. Intente más tarde o escriba los datos a mano.");
    }
}
