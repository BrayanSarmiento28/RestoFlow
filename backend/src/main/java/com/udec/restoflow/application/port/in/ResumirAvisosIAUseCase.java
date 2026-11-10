package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.ia.RespuestaIA;
import com.udec.restoflow.domain.model.Rol;

/** RestoFlow AI · resumen de la campana escrito por la IA externa, para el rol de quien consulta. */
public interface ResumirAvisosIAUseCase {

    /** Si la IA no responde, devuelve un resumen hecho con reglas (origen REGLAS); nunca falla por la IA. */
    RespuestaIA resumir(Rol rol, Long usuarioId);
}
