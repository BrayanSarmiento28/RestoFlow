package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.Alerta;

import java.util.List;

/** RF-07 · Alertas de bajo inventario. Las crea el sistema; las personas las marcan como atendidas. */
public interface GestionarAlertasUseCase {

    /** De la más reciente a la más antigua. */
    List<Alerta> listar(boolean soloPendientes);

    Alerta atender(Long id);
}
