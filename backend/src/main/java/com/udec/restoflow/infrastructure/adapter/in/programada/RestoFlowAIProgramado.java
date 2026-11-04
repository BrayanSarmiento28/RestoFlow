package com.udec.restoflow.infrastructure.adapter.in.programada;

import com.udec.restoflow.application.port.in.GenerarAlertasIAUseCase;
import com.udec.restoflow.application.port.in.GenerarAlertasIAUseCase.ResultadoAlertas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RES-40 · Adaptador de entrada "por reloj": cada hora le pide a RestoFlow AI que revise el inventario y cree o
 * cierre sus alertas. Es un adaptador como un controlador REST, solo que lo activa el tiempo y no una petición.
 * <p>
 * Horario: {@code restoflow.ia.cron} (por defecto al minuto 5 de cada hora). Se apaga con
 * {@code IA_ALERTAS_PROGRAMADAS=false}; en las pruebas automáticas está apagado.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "restoflow.ia.alertas-programadas", havingValue = "true", matchIfMissing = true)
public class RestoFlowAIProgramado {

    private static final Logger log = LoggerFactory.getLogger(RestoFlowAIProgramado.class);

    private final GenerarAlertasIAUseCase alertas;

    public RestoFlowAIProgramado(GenerarAlertasIAUseCase alertas) {
        this.alertas = alertas;
    }

    @Scheduled(cron = "${restoflow.ia.cron:0 5 * * * *}")
    public void revisarInventario() {
        try {
            ResultadoAlertas resultado = alertas.generarAlertas();
            log.info("RestoFlow AI revisó el inventario: {} alertas nuevas, {} cerradas, {} ya avisados",
                    resultado.creadas(), resultado.cerradas(), resultado.yaAvisados());
        } catch (RuntimeException e) {
            // Un fallo de la tarea no debe detener las siguientes ejecuciones; queda en el log para revisarlo
            log.error("RestoFlow AI no pudo revisar el inventario", e);
        }
    }
}
