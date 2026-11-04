package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.AnalisisVentas.ProductoTendencia;
import com.udec.restoflow.domain.ia.Recomendacion.Tipo;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.UnidadMedida;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * RF-11 · Convierte el análisis de insumos y de ventas en recomendaciones concretas, en español sencillo.
 * <ul>
 *   <li>COMPRA: insumos que se acaban en 7 días o menos, con la cantidad a comprar.</li>
 *   <li>TENDENCIA: insumos cuyo consumo subió 20 % o más (RES-38).</li>
 *   <li>DEMANDA: mañana se espera 20 % más de ventas que un día promedio: revisar el inventario antes.</li>
 *   <li>PRODUCTO: productos que suben o bajan 30 % o más sus ventas.</li>
 *   <li>INFORMACION: todavía no hay suficientes días de ventas para confiar en las predicciones.</li>
 * </ul>
 * Quedan ordenadas por prioridad: ALTA, MEDIA y BAJA.
 */
public final class GeneradorRecomendaciones {

    static final int UMBRAL_TENDENCIA_INSUMO = 20;
    static final int UMBRAL_DEMANDA = 20;
    static final int UMBRAL_PRODUCTO = 30;
    /** Un producto necesita un mínimo de unidades para que su cambio en % signifique algo. */
    static final int UNIDADES_MINIMAS_PRODUCTO = 5;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private GeneradorRecomendaciones() {
    }

    public static List<Recomendacion> generar(List<PronosticoInsumo> insumos, AnalisisVentas ventas) {
        List<Recomendacion> resultado = new ArrayList<>();
        for (PronosticoInsumo i : insumos) {
            if (i.requiereCompra() && i.cantidadSugerida().signum() > 0) {
                resultado.add(compra(i));
            } else if (!i.requiereCompra() && i.tendenciaPorcentaje() != null
                    && i.tendenciaPorcentaje() >= UMBRAL_TENDENCIA_INSUMO && i.consumoSemana().signum() > 0) {
                resultado.add(new Recomendacion(Tipo.TENDENCIA, PrioridadAlerta.BAJA, "Sube el consumo de " + i.nombre(),
                        "Se usó " + i.tendenciaPorcentaje() + " % más que la semana anterior (" + numero(i.consumoSemana())
                                + " " + abreviatura(i.unidad()) + " en 7 días). Vigile su stock.",
                        i.ingredienteId(), null));
            }
        }
        if (ventas != null) {
            Recomendacion demanda = demanda(ventas, insumos);
            if (demanda != null) {
                resultado.add(demanda);
            }
            ventas.productos().forEach(p -> {
                Recomendacion r = producto(p);
                if (r != null) {
                    resultado.add(r);
                }
            });
            if (!ventas.datosSuficientes()) {
                resultado.add(new Recomendacion(Tipo.INFORMACION, PrioridadAlerta.BAJA, "RestoFlow AI está aprendiendo",
                        "Hay " + ventas.diasConVentas() + " días con ventas en las últimas 4 semanas. Las predicciones "
                                + "son confiables desde " + AnalizadorVentas.DIAS_MINIMOS + " días.",
                        null, null));
            }
        }
        return resultado.stream().sorted(Comparator.comparing(Recomendacion::prioridad)).toList();
    }

    private static Recomendacion compra(PronosticoInsumo i) {
        String unidad = abreviatura(i.unidad());
        StringBuilder detalle = new StringBuilder();
        if (i.stockActual().signum() == 0) {
            detalle.append("Se agotó.");
        } else if (i.diasCobertura() != null) {
            detalle.append("Alcanza para ").append(dias(i.diasCobertura())).append(" al ritmo actual (")
                    .append(numero(i.consumoDiario())).append(' ').append(unidad).append(" al día).");
        } else {
            detalle.append("Está en su mínimo (").append(numero(i.stockMinimo())).append(' ').append(unidad).append(").");
        }
        if (i.tendenciaPorcentaje() != null && i.tendenciaPorcentaje() >= UMBRAL_TENDENCIA_INSUMO) {
            detalle.append(" El consumo subió ").append(i.tendenciaPorcentaje()).append(" % esta semana.");
        }
        return new Recomendacion(Tipo.COMPRA, i.prioridad(),
                "Comprar " + numero(i.cantidadSugerida()) + " " + unidad + " de " + i.nombre(), detalle.toString(),
                i.ingredienteId(), null);
    }

    /** RF-11 · "Revisar inventario antes de un aumento esperado de demanda". */
    private static Recomendacion demanda(AnalisisVentas ventas, List<PronosticoInsumo> insumos) {
        Prediccion p = ventas.prediccion();
        if (p == null || !ventas.datosSuficientes() || p.variacionPromedio() == null
                || p.variacionPromedio() < UMBRAL_DEMANDA) {
            return null;
        }
        StringBuilder detalle = new StringBuilder("Ventas esperadas: ").append(pesos(p.ventasEsperadas())).append(", ")
                .append(p.variacionPromedio()).append(" % más que un día promedio.");
        if (!p.productos().isEmpty()) {
            detalle.append(" Lo más pedido: ").append(p.productos().stream().limit(3)
                    .map(d -> d.nombre() + " (" + d.unidades() + ")").collect(Collectors.joining(", "))).append('.');
        }
        long porComprar = insumos.stream().filter(PronosticoInsumo::requiereCompra).count();
        if (porComprar > 0) {
            detalle.append(" Revise antes el inventario: ").append(porComprar)
                    .append(porComprar == 1 ? " insumo necesita compra." : " insumos necesitan compra.");
        }
        String dia = p.fecha().getDayOfWeek().getDisplayName(TextStyle.FULL, COLOMBIA);
        Long productoId = p.productos().isEmpty() ? null : p.productos().get(0).productoId();
        return new Recomendacion(Tipo.DEMANDA, PrioridadAlerta.MEDIA, "Mañana " + dia + " se espera más demanda",
                detalle.toString(), null, productoId);
    }

    private static Recomendacion producto(ProductoTendencia p) {
        Integer t = p.tendenciaPorcentaje();
        if (t == null || Math.max(p.unidadesSemana(), p.unidadesSemanaAnterior()) < UNIDADES_MINIMAS_PRODUCTO) {
            return null;
        }
        String comparacion = "Se vendieron " + p.unidadesSemana() + " esta semana frente a " + p.unidadesSemanaAnterior()
                + " la anterior (" + (t > 0 ? "+" : "") + t + " %).";
        if (t >= UMBRAL_PRODUCTO) {
            return new Recomendacion(Tipo.PRODUCTO, PrioridadAlerta.BAJA, "Crecen las ventas de " + p.nombre(),
                    comparacion + " Asegure los insumos de su receta.", null, p.productoId());
        }
        if (t <= -UMBRAL_PRODUCTO) {
            return new Recomendacion(Tipo.PRODUCTO, PrioridadAlerta.BAJA, "Bajan las ventas de " + p.nombre(),
                    comparacion + " Revise su precio, su presentación o si faltó algún insumo.", null, p.productoId());
        }
        return null;
    }

    /** Lo usan también las alertas automáticas, para que el mensaje se lea igual en las dos partes. */
    public static String abreviatura(UnidadMedida unidad) {
        return switch (unidad) {
            case GRAMO -> "g";
            case KILOGRAMO -> "kg";
            case MILILITRO -> "ml";
            case LITRO -> "l";
            case UNIDAD -> "und";
        };
    }

    /** "1 día", "2,5 días". */
    public static String dias(BigDecimal cantidad) {
        return numero(cantidad) + (cantidad.compareTo(BigDecimal.ONE) == 0 ? " día" : " días");
    }

    /** 4.380 · 2,1 · 0,5 (formato colombiano). */
    public static String numero(BigDecimal valor) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(COLOMBIA)).format(valor);
    }

    static String pesos(BigDecimal valor) {
        return "$ " + new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(COLOMBIA)).format(valor);
    }
}
