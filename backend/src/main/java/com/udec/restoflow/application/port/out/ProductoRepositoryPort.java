package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.Producto;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: guardar y consultar los productos del menú. */
public interface ProductoRepositoryPort {

    Producto guardar(Producto producto);

    Optional<Producto> buscarPorId(Long id);

    /** Lista los productos; si soloActivos es true, solo los disponibles para la venta. */
    List<Producto> listar(boolean soloActivos, Long categoriaId);

    long contar();
}
