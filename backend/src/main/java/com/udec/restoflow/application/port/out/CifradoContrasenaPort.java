package com.udec.restoflow.application.port.out;

/**
 * Puerto de salida para cifrar contraseñas y códigos.
 * La implementación usa BCrypt, pero la aplicación no lo sabe ni le importa.
 */
public interface CifradoContrasenaPort {

    String cifrar(String textoPlano);

    boolean coincide(String textoPlano, String hash);
}
