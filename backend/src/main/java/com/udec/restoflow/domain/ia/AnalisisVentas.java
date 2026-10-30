package com.udec.restoflow.domain.ia;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * RF-09 · Resultado del análisis de las ventas de las últimas 4 semanas (días completos, hasta ayer).
 *
 * @param diasConVentas          días distintos con al menos una venta en las 4 semanas
 * @param datosSuficientes       hay al menos 7 días con ventas: las predicciones son confiables
 * @param ventasSemana           total vendido en los últimos 7 días completos
 * @param tendenciaVentas        cambio frente a los 7 días anteriores, en % (null si no hay con qué comparar)
 * @param diaMasFuerte           día de la semana con más ventas en promedio
 * @param horaPico               hora del día con más pedidos cobrados (0 a 23)
 * @param productos              productos más vendidos de la semana, con su tendencia
 * @param ventasPorDia           promedio de ventas de cada día de la semana, de lunes a domingo
 * @param prediccion             demanda esperada para mañana (null si no hay ventas)
 */
public record AnalisisVentas(int diasConVentas, boolean datosSuficientes, BigDecimal ventasSemana,
                             Integer tendenciaVentas, DayOfWeek diaMasFuerte, Integer horaPico,
                             List<ProductoTendencia> productos, List<VentaDia> ventasPorDia,
                             Prediccion prediccion) {

    /** Unidades vendidas de un producto en la semana y la anterior. */
    public record ProductoTendencia(Long productoId, String nombre, int unidadesSemana, int unidadesSemanaAnterior,
                                    Integer tendenciaPorcentaje) { }

    /** Promedio vendido en un día de la semana (sobre los días de ese tipo que tuvieron ventas). */
    public record VentaDia(DayOfWeek dia, BigDecimal promedio) { }

    /**
     * RES-41 · Predicción básica: promedio de los mismos días de la semana de las últimas 4 semanas.
     *
     * @param basadoEnDias       cuántos días del historial se usaron para calcularla
     * @param variacionPromedio  % frente a un día promedio de la semana (null si no hay promedio)
     */
    public record Prediccion(LocalDate fecha, BigDecimal ventasEsperadas, int pedidosEsperados,
                             int basadoEnDias, Integer variacionPromedio, List<DemandaProducto> productos) { }

    /** Unidades que se espera vender de un producto. */
    public record DemandaProducto(Long productoId, String nombre, int unidades) { }
}
