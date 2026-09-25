package com.udec.restoflow.infrastructure.security;

import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida: implementa CifradoContrasenaPort con BCrypt.
 * BCrypt agrega un valor aleatorio ("sal") a cada contraseña, así dos usuarios con la misma
 * contraseña tienen hashes distintos, y es lento a propósito para dificultar ataques de fuerza bruta.
 */
@Component
public class BCryptCifradoAdapter implements CifradoContrasenaPort {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String cifrar(String textoPlano) {
        return bcrypt.encode(textoPlano);
    }

    @Override
    public boolean coincide(String textoPlano, String hash) {
        return textoPlano != null && hash != null && bcrypt.matches(textoPlano, hash);
    }
}
