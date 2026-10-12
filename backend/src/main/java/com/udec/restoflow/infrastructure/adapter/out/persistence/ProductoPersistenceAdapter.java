package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.ProductoEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.CategoriaJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.ProductoJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adaptador de salida: implementa ProductoRepositoryPort con JPA y SQL Server. */
@Component
public class ProductoPersistenceAdapter implements ProductoRepositoryPort {

    private final ProductoJpaRepository repositorio;
    private final CategoriaJpaRepository categorias;

    public ProductoPersistenceAdapter(ProductoJpaRepository repositorio, CategoriaJpaRepository categorias) {
        this.repositorio = repositorio;
        this.categorias = categorias;
    }

    @Override
    public Producto guardar(Producto producto) {
        ProductoEntity entidad = producto.getId() == null
                ? new ProductoEntity()
                : repositorio.findById(producto.getId()).orElseGet(ProductoEntity::new);
        entidad.setNombre(producto.getNombre());
        entidad.setDescripcion(producto.getDescripcion());
        entidad.setPrecio(producto.getPrecio());
        entidad.setCategoria(categorias.getReferenceById(producto.getCategoriaId()));
        entidad.setActivo(producto.isActivo());
        return PersistenceMapper.aDominio(repositorio.save(entidad));
    }

    @Override
    public Optional<Producto> buscarPorId(Long id) {
        return repositorio.findById(id).map(PersistenceMapper::aDominio);
    }

    @Override
    public List<Producto> listar(boolean soloActivos, Long categoriaId) {
        return repositorio.buscar(soloActivos, categoriaId).stream().map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public long contar() {
        return repositorio.count();
    }
}
