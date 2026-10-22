package com.udec.restoflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de arranque del backend de RestoFlow.
 * Spring Boot busca los componentes de la aplicación a partir de este paquete (com.udec.restoflow).
 * <p>
 * Se excluye UserDetailsServiceAutoConfiguration porque RestoFlow maneja su propio login con JWT
 * (así Spring no crea un usuario de prueba con contraseña aleatoria).
 * <p>
 * {@code @ConfigurationPropertiesScan} activa RestoFlowProperties: la configuración tipada y validada al arrancar.
 */
@ConfigurationPropertiesScan
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class RestoFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestoFlowApplication.class, args);
    }
}
