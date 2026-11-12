package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.Pedido;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RestoFlow AI · cuánto tarda normalmente cada plato, aprendido de los pedidos reales (desde que se registra hasta
 * que la cocina lo marca Listo).
 * <p>
 * Se usa la <b>mediana</b> y no el promedio: si un día un pedido se quedó olvidado 2 horas, el promedio se dispara,
 * pero la mediana (el valor del medio) casi no cambia. Con menos de {@value #MUESTRAS_MINIMAS} pedidos de un plato
 * todavía no hay datos suficientes y se usa un tiempo por defecto de {@value #MINUTOS_POR_DEFECTO} minutos.
 */
public final class TiemposPreparacion {

    public static final int MUESTRAS_MINIMAS = 3;
    public static final int MINUTOS_POR_DEFECTO = 15;
    /** Ningún plato se espera en menos de 5 minutos (evita avisos por segundos de diferencia). */
    static final int MINUTOS_MINIMOS = 5;

    private final Map<Long, Integer> medianas;

    private TiemposPreparacion(Map<Long, Integer> medianas) {
        this.medianas = medianas;
    }

    /** Aprende de los pedidos que ya llegaron a Listo. */
    public static TiemposPreparacion aprender(List<Pedido> historial) {
        Map<Long, List<Long>> muestras = muestras(historial, new HashMap<>());
        Map<Long, Integer> medianas = new HashMap<>();
        muestras.forEach((id, lista) -> {
            if (lista.size() >= MUESTRAS_MINIMAS) {
                medianas.put(id, (int) Math.max(MINUTOS_MINIMOS, mediana(lista)));
            }
        });
        return new TiemposPreparacion(medianas);
    }

    /** Minutos normales de un plato (el tiempo por defecto si aún no hay datos suficientes). */
    public int minutosNormales(Long productoId) {
        return medianas.getOrDefault(productoId, MINUTOS_POR_DEFECTO);
    }

    public boolean tieneDatos(Long productoId) {
        return medianas.containsKey(productoId);
    }

    /** El plato que más se demora del pedido: es el que marca cuándo debería estar listo. */
    public DetallePedido platoMasLento(Pedido pedido) {
        DetallePedido lento = null;
        for (DetallePedido d : pedido.getDetalles()) {
            if (lento == null || minutosNormales(d.getProductoId()) > minutosNormales(lento.getProductoId())) {
                lento = d;
            }
        }
        return lento;
    }

    public int minutosNormales(Pedido pedido) {
        DetallePedido lento = platoMasLento(pedido);
        return lento == null ? MINUTOS_POR_DEFECTO : minutosNormales(lento.getProductoId());
    }

    /**
     * RF-09 · Estadística de tiempos de preparación por plato: cuántos pedidos se midieron, el tiempo normal
     * (mediana) y el promedio, del plato más lento al más rápido. A diferencia de {@link #aprender}, incluye los
     * platos con pocas muestras: es un reporte de lo que pasó, no una predicción.
     */
    public static List<TiempoPlato> porPlato(List<Pedido> pedidos) {
        Map<Long, String> nombres = new HashMap<>();
        Map<Long, List<Long>> muestras = muestras(pedidos, nombres);
        return muestras.entrySet().stream()
                .map(e -> new TiempoPlato(e.getKey(), nombres.get(e.getKey()), e.getValue().size(),
                        mediana(e.getValue()),
                        Math.round(e.getValue().stream().mapToLong(Long::longValue).average().orElse(0))))
                .sorted(Comparator.comparingLong(TiempoPlato::medianaMinutos).reversed()
                        .thenComparing(TiempoPlato::nombre, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Minutos desde que se registró el pedido hasta que la cocina lo marcó Listo, por plato. */
    private static Map<Long, List<Long>> muestras(List<Pedido> pedidos, Map<Long, String> nombres) {
        Map<Long, List<Long>> muestras = new HashMap<>();
        for (Pedido p : pedidos) {
            // Un pedido con adición no sirve de muestra: su hora de Listo incluye lo que se pidió después
            if (p.tieneAdicion() || p.getFechaListo() == null || p.getFechaCreacion() == null
                    || p.getFechaListo().isBefore(p.getFechaCreacion())) {
                continue;
            }
            long minutos = Duraciones.minutosEntre(p.getFechaCreacion(), p.getFechaListo());
            for (DetallePedido d : p.getDetalles()) {
                nombres.putIfAbsent(d.getProductoId(), d.getProductoNombre());
            }
            p.getDetalles().stream().map(DetallePedido::getProductoId).distinct()
                    .forEach(id -> muestras.computeIfAbsent(id, k -> new ArrayList<>()).add(minutos));
        }
        return muestras;
    }

    /** RF-09 · Tiempo de preparación de un plato en un periodo. */
    public record TiempoPlato(Long productoId, String nombre, int pedidos, long medianaMinutos,
                              long promedioMinutos) { }

    static long mediana(List<Long> valores) {
        List<Long> ordenados = valores.stream().sorted().toList();
        int medio = ordenados.size() / 2;
        return ordenados.size() % 2 == 1 ? ordenados.get(medio)
                : Math.round((ordenados.get(medio - 1) + ordenados.get(medio)) / 2.0);
    }
}
