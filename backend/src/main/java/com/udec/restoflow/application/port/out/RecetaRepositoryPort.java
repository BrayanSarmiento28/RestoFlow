package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.RecetaIngrediente;

import java.util.Collection;
import java.util.List;

/** Puerto de salida: recetas (qué insumos lleva cada producto). */
public interface RecetaRepositoryPort {

    List<RecetaIngrediente> deProducto(Long productoId);

    List<RecetaIngrediente> deProductos(Collection<Long> productoIds);

    /** Borra la receta anterior del producto y guarda la nueva. */
    List<RecetaIngrediente> reemplazar(Long productoId, List<RecetaIngrediente> lineas);
}
