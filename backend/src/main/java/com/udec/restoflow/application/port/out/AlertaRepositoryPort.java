package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.Alerta;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: alertas de inventario. */
public interface AlertaRepositoryPort {

    Alerta guardar(Alerta alerta);

    Optional<Alerta> buscarPorId(Long id);

    /** De la más reciente a la más antigua. */
    List<Alerta> listar(boolean soloPendientes);

    List<Alerta> pendientesDe(Long ingredienteId);

    long contarPendientes();
}
