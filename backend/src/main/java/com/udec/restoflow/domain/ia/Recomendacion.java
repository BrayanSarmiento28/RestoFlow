package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.model.PrioridadAlerta;

/**
 * RF-11 · Una recomendación de RestoFlow AI, lista para mostrar al administrador.
 *
 * @param ingredienteId insumo al que se refiere (null si no es de un insumo)
 * @param productoId    producto al que se refiere (null si no es de un producto)
 */
public record Recomendacion(Tipo tipo, PrioridadAlerta prioridad, String titulo, String detalle, Long ingredienteId,
                            Long productoId) {

    public enum Tipo {
        /** Comprar un insumo antes de que se acabe. */
        COMPRA,
        /** Un insumo se está gastando más rápido que la semana anterior. */
        TENDENCIA,
        /** Se espera un aumento de la demanda (revisar el inventario antes). */
        DEMANDA,
        /** Un producto sube o baja sus ventas. */
        PRODUCTO,
        /** Información sobre el propio análisis (por ejemplo, que faltan datos). */
        INFORMACION
    }
}
