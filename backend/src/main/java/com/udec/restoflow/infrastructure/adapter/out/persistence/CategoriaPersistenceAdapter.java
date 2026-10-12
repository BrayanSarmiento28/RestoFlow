package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.CategoriaRepositoryPort;
import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.CategoriaEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.CategoriaJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adaptador de salida: implementa CategoriaRepositoryPort con JPA y SQL Server. */
@Component
public class CategoriaPersistenceAdapter implements CategoriaRepositoryPort {

    private final CategoriaJpaRepository repositorio;

    public CategoriaPersistenceAdapter(CategoriaJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Categoria guardar(Categoria categoria) {
        CategoriaEntity entidad = categoria.getId() == null
                ? new CategoriaEntity()
                : repositorio.findById(categoria.getId()).orElseGet(CategoriaEntity::new);
        entidad.setNombre(categoria.getNombre());
        entidad.setActiva(categoria.isActiva());
        return PersistenceMapper.aDominio(repositorio.save(entidad));
    }

    @Override
    public Optional<Categoria> buscarPorId(Long id) {
        return repositorio.findById(id).map(PersistenceMapper::aDominio);
    }

    @Override
    public boolean existePorNombre(String nombre) {
        return repositorio.existsByNombreIgnoreCase(nombre);
    }

    @Override
    public List<Categoria> listarTodas() {
        return repositorio.findAllByOrderByNombreAsc().stream().map(PersistenceMapper::aDominio).toList();
    }
}
