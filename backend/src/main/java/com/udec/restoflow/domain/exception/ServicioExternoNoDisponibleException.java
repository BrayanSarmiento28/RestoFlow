package com.udec.restoflow.domain.exception;

/**
 * Un servicio de terceros (Open Food Facts, el correo de Gmail...) no respondió o respondió con error.
 * El API lo devuelve como 503: no es culpa del usuario y puede intentarlo más tarde.
 * <p>
 * SOLID - D: la capa web solo conoce esta excepción general; cada adaptador de salida lanza la suya
 * (por ejemplo CorreoNoEnviadoException) heredando de esta, sin que la web dependa de ese adaptador.
 */
public class ServicioExternoNoDisponibleException extends RuntimeException {

    public ServicioExternoNoDisponibleException(String servicio) {
        super(servicio + " no está disponible en este momento. Intente más tarde o escriba los datos a mano.");
    }

    /** Para los adaptadores que necesitan un mensaje propio y conservar la causa técnica (para el log). */
    protected ServicioExternoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
