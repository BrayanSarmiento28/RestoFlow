package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.RecetaIngrediente;

import java.math.BigDecimal;
import java.util.List;

/** RF-06 · Qué insumos y cuánto de cada uno lleva cada producto del menú. Lo define el administrador. */
public interface GestionarRecetasUseCase {

    List<RecetaIngrediente> receta(Long productoId);

    /** Reemplaza la receta completa del producto. Una lista vacía deja el producto sin receta. */
    List<RecetaIngrediente> definir(Long productoId, List<LineaReceta> lineas);

    record LineaReceta(Long ingredienteId, BigDecimal cantidad) { }
}
