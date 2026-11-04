package com.udec.restoflow.infrastructure.adapter.in.programada;

import com.udec.restoflow.application.port.in.LimpiarRegistroUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada madrugada (3:30 a. m. por defecto) borra los registros de actividad más viejos que el tiempo de
 * conservación ({@code REGISTRO_BD_DIAS}, 30 por defecto). Así la tabla no crece sin límite.
 * Se apaga con {@code REGISTRO_BD_LIMPIEZA=false}; en las pruebas automáticas está apagada.
 */
@Component
@ConditionalOnProperty(name = "restoflow.registro.limpieza-programada", havingValue = "true", matchIfMissing = true)
public class LimpiezaRegistroProgramada {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaRegistroProgramada.class);

    private final LimpiarRegistroUseCase limpiar;

    public LimpiezaRegistroProgramada(LimpiarRegistroUseCase limpiar) {
        this.limpiar = limpiar;
    }

    @Scheduled(cron = "${restoflow.registro.cron-limpieza:0 30 3 * * *}")
    public void limpiar() {
        try {
            limpiar.limpiar();
        } catch (RuntimeException e) {
            log.error("No se pudo limpiar el registro de actividad", e);
        }
    }
}
