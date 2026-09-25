package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.model.Usuario;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.UsuarioEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.UsuarioJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adaptador de salida: implementa el puerto UsuarioRepositoryPort usando JPA y SQL Server. */
@Component
public class UsuarioPersistenceAdapter implements UsuarioRepositoryPort {

    private final UsuarioJpaRepository repositorio;

    public UsuarioPersistenceAdapter(UsuarioJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        UsuarioEntity entidad = usuario.getId() == null
                ? new UsuarioEntity()
                : repositorio.findById(usuario.getId()).orElseGet(UsuarioEntity::new);
        entidad.setNombre(usuario.getNombre());
        entidad.setEmail(usuario.getEmail());
        entidad.setPasswordHash(usuario.getPasswordHash());
        entidad.setRol(usuario.getRol());
        entidad.setActivo(usuario.isActivo());
        entidad.setFechaCreacion(usuario.getFechaCreacion());
        return PersistenceMapper.aDominio(repositorio.save(entidad));
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return repositorio.findById(id).map(PersistenceMapper::aDominio);
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return repositorio.findByEmail(email).map(PersistenceMapper::aDominio);
    }

    @Override
    public boolean existePorEmail(String email) {
        return repositorio.existsByEmail(email);
    }

    @Override
    public List<Usuario> listarTodos() {
        return repositorio.findAllByOrderByNombreAsc().stream().map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public long contar() {
        return repositorio.count();
    }
}
