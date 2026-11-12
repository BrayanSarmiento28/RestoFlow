package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.AlertaRepositoryPort;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.AlertaEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.AlertaJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.IngredienteJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adaptador de salida: alertas en la tabla "alertas". */
@Component
public class AlertaPersistenceAdapter implements AlertaRepositoryPort {

    private final AlertaJpaRepository repositorio;
    private final IngredienteJpaRepository ingredientes;

    public AlertaPersistenceAdapter(AlertaJpaRepository repositorio, IngredienteJpaRepository ingredientes) {
        this.repositorio = repositorio;
        this.ingredientes = ingredientes;
    }

    @Override
    public Alerta guardar(Alerta alerta) {
        AlertaEntity e = alerta.getId() == null
                ? new AlertaEntity()
                : repositorio.findById(alerta.getId()).orElseGet(AlertaEntity::new);
        e.setIngrediente(alerta.getIngredienteId() == null ? null : ingredientes.getReferenceById(alerta.getIngredienteId()));
        e.setMensaje(alerta.getMensaje());
        e.setPrioridad(alerta.getPrioridad());
        e.setAtendida(alerta.isAtendida());
        e.setFechaCreacion(alerta.getFechaCreacion());
        e.setFechaAtencion(alerta.getFechaAtencion());
        AlertaEntity guardada = repositorio.save(e);
        return new Alerta(guardada.getId(), alerta.getIngredienteId(), guardada.getMensaje(), guardada.getPrioridad(),
                guardada.isAtendida(), guardada.getFechaCreacion(), guardada.getFechaAtencion(),
                alerta.getIngredienteNombre());
    }

    @Override
    public Optional<Alerta> buscarPorId(Long id) {
        return repositorio.findById(id).map(PersistenceMapper::aDominio);
    }

    @Override
    public List<Alerta> listar(boolean soloPendientes) {
        List<AlertaEntity> lista = soloPendientes
                ? repositorio.findByAtendidaFalseOrderByFechaCreacionDescIdDesc()
                : repositorio.findAllByOrderByFechaCreacionDescIdDesc();
        return lista.stream().map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public List<Alerta> pendientesDe(Long ingredienteId) {
        return repositorio.findByIngredienteIdAndAtendidaFalse(ingredienteId).stream()
                .map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public long contarPendientes() {
        return repositorio.countByAtendidaFalse();
    }
}
