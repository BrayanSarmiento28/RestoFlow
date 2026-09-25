package com.udec.restoflow.infrastructure.adapter.out.mail;

/** Error técnico: el servidor de correo no respondió o rechazó el envío. */
public class CorreoNoEnviadoException extends RuntimeException {

    public CorreoNoEnviadoException(Throwable causa) {
        super("No fue posible enviar el correo en este momento. Intente de nuevo más tarde", causa);
    }
}
