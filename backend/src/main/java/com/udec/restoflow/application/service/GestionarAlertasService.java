package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarAlertasUseCase;
import com.udec.restoflow.application.port.out.AlertaRepositoryPort;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Alerta;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** RF-07 · Consultar y atender las alertas de inventario. */
@Transactional
public class GestionarAlertasService implements GestionarAlertasUseCase {

    private final AlertaRepositoryPort alertas;
    private final Clock reloj;

    public GestionarAlertasService(AlertaRepositoryPort alertas, Clock reloj) {
        this.alertas = alertas;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alerta> listar(boolean soloPendientes) {
        return alertas.listar(soloPendientes);
    }

    @Override
    public Alerta atender(Long id) {
        Alerta alerta = alertas.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Alerta", id));
        alerta.marcarAtendida(LocalDateTime.now(reloj));
        return alertas.guardar(alerta);
    }
}
