package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.TipoMovimiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * RF-10 / RF-11 · Motor de abastecimiento de RestoFlow AI. Para cada insumo calcula:
 * <table>
 *   <caption>Fórmulas</caption>
 *   <tr><td>Consumo diario</td><td>salidas de los últimos 7 días ÷ 7</td></tr>
 *   <tr><td>Tendencia (RES-38)</td><td>(semana actual − semana anterior) ÷ semana anterior × 100</td></tr>
 *   <tr><td>Días de cobertura</td><td>stock actual ÷ consumo diario</td></tr>
 *   <tr><td>Prioridad</td><td>ALTA si alcanza para 3 días o menos (o ya está en su mínimo); MEDIA si para 7 o menos</td></tr>
 *   <tr><td>Cantidad sugerida</td><td>consumo diario × 7 × (1 + tendencia positiva) + stock mínimo − stock actual</td></tr>
 * </table>
 * Las salidas incluyen el consumo por ventas y las mermas: las dos gastan el insumo de verdad.
 * Se usan <b>días completos</b> (hasta ayer): si se contara el día de hoy a medias, en la mañana el consumo
 * parecería más bajo de lo que es.
 * Es Java puro: recibe los datos y devuelve el resultado, sin consultar la base de datos.
 */
public final class AnalizadorAbastecimiento {

    /** Días de la ventana de análisis (una semana). */
    public static final int DIAS_VENTANA = 7;
    public static final BigDecimal DIAS_PRIORIDAD_ALTA = new BigDecimal("3");
    static final BigDecimal DIAS_PRIORIDAD_MEDIA = new BigDecimal("7");
    /** La tendencia sube la compra sugerida como máximo al doble, para que un pico raro no dispare la compra. */
    private static final int TENDENCIA_MAXIMA = 100;
    private static final BigDecimal CIEN = new BigDecimal("100");

    private AnalizadorAbastecimiento() {
    }

    /**
     * @param movimientos movimientos de inventario de las últimas dos semanas (puede traer otros insumos y
     *                    tipos: aquí se toman solo las salidas de este insumo)
     * @param hoy         día del análisis; la semana son los 7 días completos anteriores (de hace 7 días a ayer)
     */
    public static PronosticoInsumo analizar(Ingrediente insumo, List<MovimientoInventario> movimientos, LocalDate hoy) {
        LocalDate ayer = hoy.minusDays(1);
        LocalDate inicioSemana = hoy.minusDays(DIAS_VENTANA);
        LocalDate inicioAnterior = inicioSemana.minusDays(DIAS_VENTANA);

        BigDecimal semanaActual = BigDecimal.ZERO;
        BigDecimal semanaAnterior = BigDecimal.ZERO;
        for (MovimientoInventario m : movimientos) {
            if (!insumo.getId().equals(m.getIngredienteId()) || m.getTipo() != TipoMovimiento.SALIDA) {
                continue;
            }
            LocalDate dia = m.getFecha().toLocalDate();
            if (!dia.isBefore(inicioSemana) && !dia.isAfter(ayer)) {
                semanaActual = semanaActual.add(m.getCantidad());
            } else if (!dia.isBefore(inicioAnterior) && dia.isBefore(inicioSemana)) {
                semanaAnterior = semanaAnterior.add(m.getCantidad());
            }
        }

        BigDecimal consumoDiario = semanaActual.divide(BigDecimal.valueOf(DIAS_VENTANA), 2, RoundingMode.HALF_UP);
        Integer tendencia = porcentajeDeCambio(semanaActual, semanaAnterior);
        BigDecimal cobertura = consumoDiario.signum() > 0
                ? insumo.getStockActual().divide(consumoDiario, 1, RoundingMode.HALF_UP)
                : null;
        PrioridadAlerta prioridad = prioridad(insumo, cobertura);
        BigDecimal sugerida = prioridad == null ? BigDecimal.ZERO : cantidadSugerida(insumo, consumoDiario, tendencia);

        return new PronosticoInsumo(insumo.getId(), insumo.getNombre(), insumo.getUnidad(), insumo.getStockActual(),
                insumo.getStockMinimo(), consumoDiario, semanaActual, tendencia, cobertura, prioridad, sugerida);
    }

    /** (actual − anterior) ÷ anterior × 100, redondeado. Null si no hay con qué comparar. */
    public static Integer porcentajeDeCambio(BigDecimal actual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() <= 0) {
            return null;
        }
        return actual.subtract(anterior).multiply(CIEN).divide(anterior, 0, RoundingMode.HALF_UP).intValue();
    }

    private static PrioridadAlerta prioridad(Ingrediente insumo, BigDecimal cobertura) {
        if (insumo.estaEnMinimo() || (cobertura != null && cobertura.compareTo(DIAS_PRIORIDAD_ALTA) <= 0)) {
            return PrioridadAlerta.ALTA;
        }
        if (cobertura != null && cobertura.compareTo(DIAS_PRIORIDAD_MEDIA) <= 0) {
            return PrioridadAlerta.MEDIA;
        }
        return null;
    }

    /**
     * Lo necesario para una semana más (ajustado por la tendencia) y para quedar sobre el mínimo, menos lo que hay.
     * Sin consumo reciente (insumo en su mínimo que no se ha usado), se sugiere llegar al doble del mínimo.
     * Se redondea hacia arriba a unidades enteras de su medida.
     */
    private static BigDecimal cantidadSugerida(Ingrediente insumo, BigDecimal consumoDiario, Integer tendencia) {
        BigDecimal objetivo;
        if (consumoDiario.signum() > 0) {
            int ajuste = tendencia == null ? 0 : Math.min(Math.max(tendencia, 0), TENDENCIA_MAXIMA);
            BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(ajuste).divide(CIEN, 2, RoundingMode.HALF_UP));
            objetivo = consumoDiario.multiply(BigDecimal.valueOf(DIAS_VENTANA)).multiply(factor)
                    .add(insumo.getStockMinimo());
        } else {
            objetivo = insumo.getStockMinimo().multiply(BigDecimal.valueOf(2));
        }
        BigDecimal faltante = objetivo.subtract(insumo.getStockActual());
        return faltante.signum() > 0 ? faltante.setScale(0, RoundingMode.CEILING) : BigDecimal.ZERO;
    }
}
