/**
 * <b>RestoFlow AI</b> (Sprint 5, RF-09 a RF-11): el motor de análisis del restaurante.
 * <p>
 * Son reglas de dominio en Java puro, sin frameworks ni servicios externos: reciben el historial (ventas y
 * movimientos de inventario) y devuelven análisis, tendencias, predicciones y recomendaciones. Por eso se pueden
 * probar con datos inventados y siempre dan el mismo resultado para los mismos datos (son explicables).
 * <ul>
 *   <li>{@link com.udec.restoflow.domain.ia.AnalizadorAbastecimiento}: consumo de cada insumo, tendencia,
 *       días que alcanza el stock y cuánto comprar (RF-10, RF-11).</li>
 *   <li>{@link com.udec.restoflow.domain.ia.AnalizadorVentas}: productos más vendidos, días y horas fuertes,
 *       tendencias y predicción básica de la demanda de mañana (RF-09).</li>
 *   <li>{@link com.udec.restoflow.domain.ia.GeneradorRecomendaciones}: convierte lo anterior en
 *       recomendaciones concretas para el administrador (RF-11).</li>
 * </ul>
 */
package com.udec.restoflow.domain.ia;
