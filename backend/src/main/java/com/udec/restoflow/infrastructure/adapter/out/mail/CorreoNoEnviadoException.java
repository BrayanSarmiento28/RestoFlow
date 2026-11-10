package com.udec.restoflow.infrastructure.adapter.out.mail;

import com.udec.restoflow.domain.exception.ServicioExternoNoDisponibleException;

/** Error técnico: el servidor de correo no respondió o rechazó el envío. */
public class CorreoNoEnviadoException extends ServicioExternoNoDisponibleException {

    public CorreoNoEnviadoException(Throwable causa) {
        super("No fue posible enviar el correo en este momento. Intente de nuevo más tarde", causa);
    }
}
