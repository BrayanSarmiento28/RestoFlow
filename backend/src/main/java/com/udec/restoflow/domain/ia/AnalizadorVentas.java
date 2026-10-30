package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.ia.AnalisisVentas.DemandaProducto;
import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.AnalisisVentas.ProductoTendencia;
import com.udec.restoflow.domain.ia.AnalisisVentas.VentaDia;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Ventas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * RF-09 · Analiza las ventas (pedidos Pagados) de las últimas 4 semanas:
 * <ul>
 *   <li>Total de la semana y su tendencia frente a la anterior (RES-38).</li>
 *   <li>Productos más vendidos con su tendencia (RES-36, RES-38).</li>
 *   <li>Día de la semana más fuerte y hora pico (comportamientos relevantes).</li>
 *   <li>Predicción básica de la demanda de mañana (RES-41): promedio de los mismos días de la semana. Por ejemplo,
 *       para un sábado se promedian los últimos sábados. Es un método simple y explicable, adecuado para un
 *       restaurante pequeño con pocas semanas de datos.</li>
 * </ul>
 * Se usan <b>días completos</b> (hasta ayer), para no comparar un día a medias con días enteros.
 * La fecha de una venta es la de cierre (cuando se cobró); la hora pico usa la hora en que se hizo el pedido.
 */
public final class AnalizadorVentas {

    public static final int SEMANAS_HISTORIAL = 4;
    /** Con menos días con ventas, los promedios todavía no son confiables. */
    public static final int DIAS_MINIMOS = 7;
    private static final int PRODUCTOS_PREDICCION = 5;

    private AnalizadorVentas() {
    }

    /**
     * @param pagados pedidos Pagados (pueden venir de más fechas: aquí se toman solo las 4 semanas que terminan ayer)
     * @param hoy     día del análisis: se analizan los 28 días completos anteriores y se predice mañana
     */
    public static AnalisisVentas analizar(List<Pedido> pagados, LocalDate hoy) {
        LocalDate ayer = hoy.minusDays(1);
        LocalDate inicio = hoy.minusDays(SEMANAS_HISTORIAL * 7L);
        LocalDate inicioSemana = hoy.minusDays(7);
        LocalDate inicioAnterior = inicioSemana.minusDays(7);

        Map<LocalDate, List<Pedido>> porDia = new TreeMap<>();
        for (Pedido p : pagados) {
            if (p.getFechaCierre() == null) {
                continue;
            }
            LocalDate dia = p.getFechaCierre().toLocalDate();
            if (!dia.isBefore(inicio) && !dia.isAfter(ayer)) {
                porDia.computeIfAbsent(dia, d -> new ArrayList<>()).add(p);
            }
        }

        List<Pedido> semana = entre(porDia, inicioSemana, ayer);
        List<Pedido> anterior = entre(porDia, inicioAnterior, inicioSemana.minusDays(1));
        BigDecimal ventasSemana = Ventas.total(semana);
        Integer tendencia = AnalizadorAbastecimiento.porcentajeDeCambio(ventasSemana, Ventas.total(anterior));

        List<VentaDia> ventasPorDia = ventasPorDiaDeLaSemana(porDia);
        DayOfWeek diaMasFuerte = ventasPorDia.stream()
                .filter(v -> v.promedio().signum() > 0)
                .max(Comparator.comparing(VentaDia::promedio))
                .map(VentaDia::dia)
                .orElse(null);

        return new AnalisisVentas(porDia.size(), porDia.size() >= DIAS_MINIMOS, ventasSemana, tendencia, diaMasFuerte,
                horaPico(porDia), productos(semana, anterior), ventasPorDia, prediccion(porDia, hoy, inicioSemana));
    }

    private static List<Pedido> entre(Map<LocalDate, List<Pedido>> porDia, LocalDate desde, LocalDate hasta) {
        List<Pedido> resultado = new ArrayList<>();
        porDia.forEach((dia, pedidos) -> {
            if (!dia.isBefore(desde) && !dia.isAfter(hasta)) {
                resultado.addAll(pedidos);
            }
        });
        return resultado;
    }

    /** De lunes a domingo: promedio de los días de ese tipo que tuvieron ventas (0 si no hubo ninguno). */
    private static List<VentaDia> ventasPorDiaDeLaSemana(Map<LocalDate, List<Pedido>> porDia) {
        List<VentaDia> resultado = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            List<BigDecimal> totales = porDia.entrySet().stream()
                    .filter(e -> e.getKey().getDayOfWeek() == dia)
                    .map(e -> Ventas.total(e.getValue()))
                    .toList();
            resultado.add(new VentaDia(dia, promedio(totales)));
        }
        return resultado;
    }

    private static Integer horaPico(Map<LocalDate, List<Pedido>> porDia) {
        Map<Integer, Integer> pedidosPorHora = new TreeMap<>();
        porDia.values().forEach(lista -> lista.forEach(p -> pedidosPorHora.merge(
                (p.getFechaCreacion() != null ? p.getFechaCreacion() : p.getFechaCierre()).getHour(), 1, Integer::sum)));
        return pedidosPorHora.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Todos los productos vendidos en las dos semanas, del más vendido al menos vendido esta semana. */
    private static List<ProductoTendencia> productos(List<Pedido> semana, List<Pedido> anterior) {
        Map<Long, String> nombres = new HashMap<>();
        Map<Long, Integer> actual = unidadesPorProducto(semana, nombres);
        Map<Long, Integer> previa = unidadesPorProducto(anterior, nombres);
        Map<Long, Integer> todos = new LinkedHashMap<>(actual);
        previa.keySet().forEach(id -> todos.putIfAbsent(id, 0));
        return todos.keySet().stream()
                .map(id -> {
                    int ahora = actual.getOrDefault(id, 0);
                    int antes = previa.getOrDefault(id, 0);
                    return new ProductoTendencia(id, nombres.get(id), ahora, antes,
                            AnalizadorAbastecimiento.porcentajeDeCambio(BigDecimal.valueOf(ahora), BigDecimal.valueOf(antes)));
                })
                .sorted(Comparator.comparingInt(ProductoTendencia::unidadesSemana).reversed()
                        .thenComparing(ProductoTendencia::nombre, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static Map<Long, Integer> unidadesPorProducto(List<Pedido> pedidos, Map<Long, String> nombres) {
        Map<Long, Integer> unidades = new LinkedHashMap<>();
        for (Pedido p : pedidos) {
            for (DetallePedido d : p.getDetalles()) {
                unidades.merge(d.getProductoId(), d.getCantidad(), Integer::sum);
                nombres.putIfAbsent(d.getProductoId(), d.getProductoNombre());
            }
        }
        return unidades;
    }

    /**
     * RES-41 · Mañana se parecerá a los mismos días de la semana de las últimas 4 semanas. Si todavía no hay
     * ninguno de ese día, se usa el promedio de los días con ventas de la última semana.
     */
    private static Prediccion prediccion(Map<LocalDate, List<Pedido>> porDia, LocalDate hoy, LocalDate inicioSemana) {
        LocalDate manana = hoy.plusDays(1);
        List<LocalDate> base = porDia.keySet().stream().filter(d -> d.getDayOfWeek() == manana.getDayOfWeek()).toList();
        if (base.isEmpty()) {
            base = porDia.keySet().stream().filter(d -> !d.isBefore(inicioSemana)).toList();
        }
        if (base.isEmpty()) {
            return null;
        }
        List<BigDecimal> totales = new ArrayList<>();
        int pedidos = 0;
        Map<Long, Integer> unidades = new LinkedHashMap<>();
        Map<Long, String> nombres = new HashMap<>();
        for (LocalDate dia : base) {
            List<Pedido> delDia = porDia.get(dia);
            totales.add(Ventas.total(delDia));
            pedidos += delDia.size();
            unidadesPorProducto(delDia, nombres).forEach((id, u) -> unidades.merge(id, u, Integer::sum));
        }
        int dias = base.size();
        BigDecimal esperado = promedio(totales);
        List<DemandaProducto> productos = unidades.entrySet().stream()
                .map(e -> new DemandaProducto(e.getKey(), nombres.get(e.getKey()), redondear(e.getValue(), dias)))
                .filter(d -> d.unidades() > 0)
                .sorted(Comparator.comparingInt(DemandaProducto::unidades).reversed())
                .limit(PRODUCTOS_PREDICCION)
                .toList();
        BigDecimal promedioDiario = promedio(porDia.values().stream().map(Ventas::total).toList());
        return new Prediccion(manana, esperado, redondear(pedidos, dias), dias,
                AnalizadorAbastecimiento.porcentajeDeCambio(esperado, promedioDiario), productos);
    }

    private static BigDecimal promedio(List<BigDecimal> valores) {
        if (valores.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valores.size()), 0, RoundingMode.HALF_UP);
    }

    private static int redondear(int total, int dias) {
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(dias), 0, RoundingMode.HALF_UP).intValue();
    }
}
