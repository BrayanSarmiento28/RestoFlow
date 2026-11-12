package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.ia.Recomendacion;
import com.udec.restoflow.domain.ia.RespuestaIA;

import java.time.LocalDateTime;
import java.util.List;

/** RestoFlow AI con IA externa: preguntas del administrador y recomendaciones redactadas por la IA. */
public interface ConsultarAsistenteIAUseCase {

    /**
     * Responde una pregunta con los datos reales del restaurante.
     *
     * @throws com.udec.restoflow.domain.exception.AsistenteIANoDisponibleException si la IA no está disponible
     *         (sin IA no hay quién conteste una pregunta libre)
     */
    RespuestaIA preguntar(String pregunta);

    /** Recomendaciones escritas por la IA; si no responde, las calculadas con reglas (origen REGLAS). */
    RecomendacionesIA recomendaciones();

    record RecomendacionesIA(List<Recomendacion> recomendaciones, RespuestaIA.Origen origen,
                             LocalDateTime generadoEn) { }
}
