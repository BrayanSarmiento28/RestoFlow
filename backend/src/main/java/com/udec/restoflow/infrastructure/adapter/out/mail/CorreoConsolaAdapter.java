package com.udec.restoflow.infrastructure.adapter.out.mail;

import com.udec.restoflow.application.port.out.EnviarCorreoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida PROVISIONAL: en lugar de enviar el correo, muestra el código en la consola.
 * Sirve para probar el flujo de "olvidé mi contraseña" mientras se configura Gmail (paso B8).
 * Es un buen ejemplo de la arquitectura hexagonal: cambiar de consola a Gmail no toca los casos de uso.
 */
@Component
public class CorreoConsolaAdapter implements EnviarCorreoPort {

    private static final Logger log = LoggerFactory.getLogger(CorreoConsolaAdapter.class);

    @Override
    public void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo, int minutosVigencia) {
        log.info("""

                ============ CORREO (modo consola) ============
                Para:   {}
                Hola {}, tu código para restablecer la contraseña es: {}
                Vence en {} minutos.
                ===============================================""", destinatario, nombre, codigo, minutosVigencia);
    }
}
