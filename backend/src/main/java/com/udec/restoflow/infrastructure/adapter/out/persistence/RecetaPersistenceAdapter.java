package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.RecetaRepositoryPort;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.RecetaIngredienteEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.IngredienteJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.ProductoJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.RecetaIngredienteJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Adaptador de salida: recetas en la tabla "receta_ingredientes". */
@Component
public class RecetaPersistenceAdapter implements RecetaRepositoryPort {

    private final RecetaIngredienteJpaRepository repositorio;
    private final ProductoJpaRepository productos;
    private final IngredienteJpaRepository ingredientes;

    public RecetaPersistenceAdapter(RecetaIngredienteJpaRepository repositorio, ProductoJpaRepository productos,
                                    IngredienteJpaRepository ingredientes) {
        this.repositorio = repositorio;
        this.productos = productos;
        this.ingredientes = ingredientes;
    }

    @Override
    public List<RecetaIngrediente> deProducto(Long productoId) {
        return deProductos(List.of(productoId));
    }

    @Override
    public List<RecetaIngrediente> deProductos(Collection<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return List.of();
        }
        return repositorio.deProductos(productoIds).stream().map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public List<RecetaIngrediente> reemplazar(Long productoId, List<RecetaIngrediente> lineas) {
        repositorio.borrarDeProducto(productoId);
        List<RecetaIngredienteEntity> nuevas = lineas.stream().map(l -> {
            RecetaIngredienteEntity e = new RecetaIngredienteEntity();
            e.setProducto(productos.getReferenceById(productoId));
            e.setIngrediente(ingredientes.getReferenceById(l.getIngredienteId()));
            e.setCantidad(l.getCantidad());
            return e;
        }).toList();
        repositorio.saveAllAndFlush(nuevas);
        return deProducto(productoId);
    }
}
