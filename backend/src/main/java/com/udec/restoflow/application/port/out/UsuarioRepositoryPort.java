package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.Usuario;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida: lo que la aplicación necesita para guardar y consultar usuarios.
 * La implementación real (JPA + SQL Server) está en infrastructure.adapter.out.persistence.
 */
public interface UsuarioRepositoryPort {

    Usuario guardar(Usuario usuario);

    Optional<Usuario> buscarPorId(Long id);

    Optional<Usuario> buscarPorEmail(String email);

    boolean existePorEmail(String email);

    List<Usuario> listarTodos();

    long contar();
}
