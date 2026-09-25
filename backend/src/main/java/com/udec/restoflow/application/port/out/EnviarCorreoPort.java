package com.udec.restoflow.application.port.out;

/** Puerto de salida para enviar correos (la implementación usa Gmail SMTP). */
public interface EnviarCorreoPort {

    void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo, int minutosVigencia);
}
