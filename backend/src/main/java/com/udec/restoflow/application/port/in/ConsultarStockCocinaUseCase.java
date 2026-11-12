package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.UnidadMedida;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Usabilidad (requerimiento no funcional) · La comanda de cocina muestra el stock de los insumos que se están
 * acabando, para que la cocina sepa antes de empezar si alcanza para el plato.
 */
public interface ConsultarStockCocinaUseCase {

    /**
     * Insumos en su mínimo o agotados que llevan los productos indicados (según su receta). Solo los que necesitan
     * atención: si nada se está acabando, la lista está vacía.
     */
    List<StockBajo> stockBajo(Collection<Long> productoIds);

    record StockBajo(Long productoId, Long ingredienteId, String insumo, BigDecimal stockActual,
                     BigDecimal stockMinimo, UnidadMedida unidad, boolean agotado) { }
}
