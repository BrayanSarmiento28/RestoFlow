package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.Ingrediente;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Puerto de salida: guardar y consultar los insumos del inventario. */
public interface IngredienteRepositoryPort {

    Ingrediente guardar(Ingrediente ingrediente);

    Optional<Ingrediente> buscarPorId(Long id);

    List<Ingrediente> buscarPorIds(Collection<Long> ids);

    /**
     * Lee los insumos para MODIFICAR su stock y los deja reservados hasta que termine la transacción: otra venta o
     * movimiento del mismo insumo espera su turno y luego lee el stock ya actualizado (sin descuentos perdidos).
     * Se reservan siempre en orden de id, para que dos transacciones no se esperen en círculo (deadlock).
     */
    List<Ingrediente> bloquearPorIds(Collection<Long> ids);

    /** Ordenados por nombre. */
    List<Ingrediente> listar(boolean soloActivos);

    boolean existePorNombre(String nombre);

    long contar();
}
