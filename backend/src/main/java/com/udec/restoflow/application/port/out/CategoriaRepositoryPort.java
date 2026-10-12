package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.Categoria;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: guardar y consultar las categorías del menú. */
public interface CategoriaRepositoryPort {

    Categoria guardar(Categoria categoria);

    Optional<Categoria> buscarPorId(Long id);

    boolean existePorNombre(String nombre);

    List<Categoria> listarTodas();
}
