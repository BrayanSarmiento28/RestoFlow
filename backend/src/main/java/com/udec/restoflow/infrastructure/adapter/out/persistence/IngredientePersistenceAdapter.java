package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.IngredienteEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.IngredienteJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adaptador de salida: insumos del inventario en la tabla "ingredientes". */
@Component
public class IngredientePersistenceAdapter implements IngredienteRepositoryPort {

    private final IngredienteJpaRepository repositorio;

    public IngredientePersistenceAdapter(IngredienteJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Ingrediente guardar(Ingrediente ingrediente) {
        IngredienteEntity entidad = ingrediente.getId() == null
                ? new IngredienteEntity()
                : repositorio.findById(ingrediente.getId()).orElseGet(IngredienteEntity::new);
        entidad.setNombre(ingrediente.getNombre());
        entidad.setUnidad(ingrediente.getUnidad());
        entidad.setStockActual(ingrediente.getStockActual());
        entidad.setStockMinimo(ingrediente.getStockMinimo());
        entidad.setActivo(ingrediente.isActivo());
        return PersistenceMapper.aDominio(repositorio.save(entidad));
    }

    @Override
    public Optional<Ingrediente> buscarPorId(Long id) {
        return repositorio.findById(id).map(PersistenceMapper::aDominio);
    }

    @Override
    public List<Ingrediente> buscarPorIds(Collection<Long> ids) {
        return repositorio.findAllById(ids).stream().map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public List<Ingrediente> listar(boolean soloActivos) {
        List<IngredienteEntity> lista = soloActivos
                ? repositorio.findByActivoTrueOrderByNombreAsc()
                : repositorio.findAllByOrderByNombreAsc();
        return lista.stream().map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public boolean existePorNombre(String nombre) {
        return repositorio.existsByNombreIgnoreCase(nombre);
    }

    @Override
    public long contar() {
        return repositorio.count();
    }
}
