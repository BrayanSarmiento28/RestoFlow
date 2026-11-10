package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.ia.AnalisisVentas;
import com.udec.restoflow.domain.ia.PronosticoInsumo;
import com.udec.restoflow.domain.ia.Recomendacion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-09 a RF-11 · RestoFlow AI: análisis de ventas e inventario con sus recomendaciones.
 * El análisis se calcula al momento con los datos de la base de datos (no se guarda), así todas las copias del
 * backend responden lo mismo (factor VI: procesos sin estado).
 */
public interface ConsultarRestoFlowAIUseCase {

    AnalisisRestoFlowAI analizar();

    /**
     * @param insumos         insumos activos, primero los más urgentes
     * @param recomendaciones de la más a la menos prioritaria
     */
    record AnalisisRestoFlowAI(LocalDateTime generadoEn, AnalisisVentas ventas, List<PronosticoInsumo> insumos,
                               List<Recomendacion> recomendaciones) { }
}
