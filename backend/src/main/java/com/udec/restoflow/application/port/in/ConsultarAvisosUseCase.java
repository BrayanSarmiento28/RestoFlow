package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.ia.Aviso;
import com.udec.restoflow.domain.model.Rol;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RestoFlow AI · avisos del momento para quien está en la aplicación (la campana de la barra superior).
 * Se calculan al consultar, con los datos reales; no se guardan (factor VI: procesos sin estado).
 */
public interface ConsultarAvisosUseCase {

    /**
     * @param rol       rol de quien consulta: solo recibe los avisos de su trabajo (el ADMIN, todos)
     * @param usuarioId para que un mesero reciba solo los de sus mesas
     */
    AvisosDelMomento avisosPara(Rol rol, Long usuarioId);

    /** @param avisos del más urgente al menos urgente */
    record AvisosDelMomento(LocalDateTime generadoEn, List<Aviso> avisos) { }
}
