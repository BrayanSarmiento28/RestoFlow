package com.udec.restoflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de arranque del backend de RestoFlow.
 * Spring Boot busca los componentes de la aplicación a partir de este paquete (com.udec.restoflow).
 */
@SpringBootApplication
public class RestoFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestoFlowApplication.class, args);
    }
}
