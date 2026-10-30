package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.UnidadMedida;

import java.math.BigDecimal;

/**
 * RF-10 · Resultado del análisis de un insumo.
 *
 * @param consumoDiario       promedio de los últimos 7 días completos (salidas ÷ 7)
 * @param consumoSemana       total consumido en los últimos 7 días completos
 * @param tendenciaPorcentaje cambio del consumo frente a la semana anterior (null si la semana anterior no tuvo consumo)
 * @param diasCobertura       días que alcanza el stock al ritmo actual (null si no hay consumo)
 * @param prioridad           ALTA: se acaba en 3 días o menos, o ya está en su mínimo · MEDIA: en 7 días o menos ·
 *                            null: no necesita compra
 * @param cantidadSugerida    cuánto comprar para cubrir una semana más el stock mínimo (0 si no hace falta)
 */
public record PronosticoInsumo(Long ingredienteId, String nombre, UnidadMedida unidad, BigDecimal stockActual,
                               BigDecimal stockMinimo, BigDecimal consumoDiario, BigDecimal consumoSemana,
                               Integer tendenciaPorcentaje, BigDecimal diasCobertura, PrioridadAlerta prioridad,
                               BigDecimal cantidadSugerida) {

    /** El insumo necesita comprarse pronto. */
    public boolean requiereCompra() {
        return prioridad != null;
    }

    public boolean enMinimo() {
        return stockActual.compareTo(stockMinimo) <= 0;
    }
}
