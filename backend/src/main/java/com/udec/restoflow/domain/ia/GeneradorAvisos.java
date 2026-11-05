package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.Aviso.Nivel;
import com.udec.restoflow.domain.ia.Aviso.Tipo;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Rol;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RestoFlow AI · avisos para cada rol, calculados con lo que pasa en este momento en el restaurante.
 * <table>
 *   <caption>Reglas</caption>
 *   <tr><th>Rol</th><th>Aviso</th><th>Atención</th><th>Urgente</th></tr>
 *   <tr><td>Cocina</td><td>Plato demorado (frente a su tiempo normal aprendido de los pedidos reales)</td>
 *       <td>pasó su tiempo normal</td><td>lleva 1,5 veces su tiempo normal (también avisa al mesero)</td></tr>
 *   <tr><td>Mesero</td><td>Pedido listo y sin llevar a la mesa</td><td>3 min</td><td>8 min</td></tr>
 *   <tr><td>Mesero</td><td>Pedido entregado y sin cobrar</td><td>30 min</td><td>60 min</td></tr>
 *   <tr><td>Inventario</td><td>Insumo en su mínimo / que alcanza para 3 días o menos</td><td>se acaba</td>
 *       <td>en el mínimo</td></tr>
 *   <tr><td>Inventario y cocina</td><td>Mañana será un día fuerte (predicción de demanda)</td>
 *       <td>20 % o más sobre un día normal</td><td>—</td></tr>
 * </table>
 * El ADMIN ve todos. Java puro: se prueba sin base de datos.
 */
public final class GeneradorAvisos {

    static final int LISTO_ATENCION_MIN = 3;
    static final int LISTO_URGENTE_MIN = 8;
    static final int SIN_COBRAR_ATENCION_MIN = 30;
    static final int SIN_COBRAR_URGENTE_MIN = 60;
    static final int DEMANDA_ALTA_PORCENTAJE = 20;
    private static final int PRODUCTOS_DEMANDA = 3;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private static final Comparator<Aviso> ORDEN = Comparator.comparing(Aviso::nivel)
            .thenComparing(Aviso::desde, Comparator.nullsLast(Comparator.naturalOrder()));

    private GeneradorAvisos() {
    }

    /**
     * @param abiertos   pedidos abiertos (en cocina, listos o entregados sin cobrar)
     * @param tiempos    tiempo normal de cada plato
     * @param insumos    pronóstico de cada insumo (vacío si no se calculó)
     * @param prediccion demanda esperada para mañana (null si no se calculó)
     */
    public static List<Aviso> generar(List<Pedido> abiertos, TiemposPreparacion tiempos, List<PronosticoInsumo> insumos,
                                      Prediccion prediccion, LocalDateTime ahora) {
        List<Aviso> avisos = new ArrayList<>();
        for (Pedido p : abiertos) {
            if (p.getEstadoPedido() != EstadoPedido.ABIERTO) {
                continue;
            }
            Aviso aviso = switch (p.getEstadoCocina()) {
                case PENDIENTE, EN_PREPARACION -> demora(p, tiempos, ahora);
                case LISTO -> listoSinEntregar(p, ahora);
                case ENTREGADO -> sinCobrar(p, ahora);
            };
            if (aviso != null) {
                avisos.add(aviso);
            }
        }
        for (PronosticoInsumo i : insumos) {
            Aviso aviso = insumo(i, ahora);
            if (aviso != null) {
                avisos.add(aviso);
            }
        }
        Aviso demanda = demanda(prediccion, ahora);
        if (demanda != null) {
            avisos.add(demanda);
        }
        return avisos.stream().sorted(ORDEN).toList();
    }

    // ---------- Cocina ----------

    static Aviso demora(Pedido p, TiemposPreparacion tiempos, LocalDateTime ahora) {
        long lleva = minutos(p.getFechaCreacion(), ahora);
        int normal = tiempos.minutosNormales(p);
        if (lleva <= normal) {
            return null;
        }
        boolean urgente = lleva >= Math.ceil(normal * 1.5);
        DetallePedido lento = tiempos.platoMasLento(p);
        String base = tiempos.tieneDatos(lento.getProductoId())
                ? "lo normal para " + lento.getProductoNombre() + " son " + normal + " min"
                : "el tiempo de referencia es " + normal + " min";
        String estado = p.getEstadoCocina() == EstadoCocina.PENDIENTE ? "Aún no se empieza a preparar." : "En preparación.";
        // Urgente: también le llega al mesero de la mesa, para que le avise al cliente
        Set<Rol> para = urgente ? Set.of(Rol.COCINA, Rol.MESERO) : Set.of(Rol.COCINA);
        return new Aviso("demora-" + p.getId(), Tipo.PLATO_DEMORADO, urgente ? Nivel.URGENTE : Nivel.ATENCION,
                "Mesa " + p.getNumeroMesa() + " · pedido #" + p.getId() + " demorado",
                "Lleva " + lleva + " min y " + base + ". " + estado, "/cocina", p.getFechaCreacion(), para,
                p.getMeseroId());
    }

    // ---------- Mesero ----------

    static Aviso listoSinEntregar(Pedido p, LocalDateTime ahora) {
        if (p.getFechaListo() == null) {
            return null;
        }
        long espera = minutos(p.getFechaListo(), ahora);
        if (espera < LISTO_ATENCION_MIN) {
            return null;
        }
        return new Aviso("listo-" + p.getId(), Tipo.PEDIDO_LISTO,
                espera >= LISTO_URGENTE_MIN ? Nivel.URGENTE : Nivel.ATENCION,
                "Mesa " + p.getNumeroMesa() + " · pedido #" + p.getId() + " listo para llevar",
                "Está listo hace " + espera + " min. Llévelo a la mesa antes de que se enfríe.", "/pedidos",
                p.getFechaListo(), Set.of(Rol.MESERO), p.getMeseroId());
    }

    static Aviso sinCobrar(Pedido p, LocalDateTime ahora) {
        if (p.getFechaEntrega() == null) {
            return null;
        }
        long espera = minutos(p.getFechaEntrega(), ahora);
        if (espera < SIN_COBRAR_ATENCION_MIN) {
            return null;
        }
        return new Aviso("cobro-" + p.getId(), Tipo.SIN_COBRAR,
                espera >= SIN_COBRAR_URGENTE_MIN ? Nivel.URGENTE : Nivel.ATENCION,
                "Mesa " + p.getNumeroMesa() + " · pedido #" + p.getId() + " sin cobrar",
                "Se entregó hace " + espera + " min y aún no se registra la venta ("
                        + GeneradorRecomendaciones.pesos(p.getTotal()) + ").",
                "/pedidos", p.getFechaEntrega(), Set.of(Rol.MESERO), p.getMeseroId());
    }

    // ---------- Inventario ----------

    static Aviso insumo(PronosticoInsumo i, LocalDateTime ahora) {
        String unidad = GeneradorRecomendaciones.abreviatura(i.unidad());
        String compra = i.cantidadSugerida() != null && i.cantidadSugerida().signum() > 0
                ? " Sugerido: comprar " + GeneradorRecomendaciones.numero(i.cantidadSugerida()) + " " + unidad + "."
                : "";
        if (i.enMinimo()) {
            return new Aviso("minimo-" + i.ingredienteId(), Tipo.INSUMO_EN_MINIMO, Nivel.URGENTE,
                    i.nombre() + " está en su mínimo",
                    "Quedan " + GeneradorRecomendaciones.numero(i.stockActual()) + " " + unidad + " (mínimo "
                            + GeneradorRecomendaciones.numero(i.stockMinimo()) + " " + unidad + ")." + compra,
                    "/inventario", ahora, Set.of(Rol.INVENTARIO), null);
        }
        if (i.diasCobertura() != null && i.diasCobertura().compareTo(AnalizadorAbastecimiento.DIAS_PRIORIDAD_ALTA) <= 0) {
            return new Aviso("seacaba-" + i.ingredienteId(), Tipo.INSUMO_SE_ACABA, Nivel.ATENCION,
                    i.nombre() + " se acaba pronto",
                    "Al ritmo actual alcanza para " + GeneradorRecomendaciones.dias(i.diasCobertura()) + "." + compra,
                    "/inventario", ahora, Set.of(Rol.INVENTARIO), null);
        }
        return null;
    }

    // ---------- Demanda (todos los que preparan el día) ----------

    static Aviso demanda(Prediccion p, LocalDateTime ahora) {
        if (p == null || p.basadoEnDias() == 0 || p.pedidosEsperados() == 0) {
            return null;
        }
        String dia = p.fecha().getDayOfWeek().getDisplayName(TextStyle.FULL, COLOMBIA);
        String productos = p.productos().stream().limit(PRODUCTOS_DEMANDA)
                .map(d -> d.nombre() + " (" + d.unidades() + ")").collect(Collectors.joining(", "));
        String masPedidos = productos.isEmpty() ? "" : " Lo más pedido: " + productos + ".";
        boolean fuerte = p.variacionPromedio() != null && p.variacionPromedio() >= DEMANDA_ALTA_PORCENTAJE;
        if (fuerte) {
            return new Aviso("demanda-" + p.fecha(), Tipo.DEMANDA, Nivel.ATENCION,
                    "Mañana " + dia + " será un día fuerte",
                    "Se esperan unos " + p.pedidosEsperados() + " pedidos, " + p.variacionPromedio()
                            + " % más que un día normal. Revise el inventario y la preparación." + masPedidos,
                    "/restoflow-ai", ahora, Set.of(Rol.INVENTARIO, Rol.COCINA), null);
        }
        // Día normal: solo informativo para el administrador
        return new Aviso("demanda-" + p.fecha(), Tipo.DEMANDA, Nivel.INFO, "Demanda esperada para mañana " + dia,
                "Se esperan unos " + p.pedidosEsperados() + " pedidos." + masPedidos, "/restoflow-ai", ahora,
                Set.of(), null);
    }

    private static long minutos(LocalDateTime desde, LocalDateTime hasta) {
        return desde == null ? 0 : Math.max(0, Duration.between(desde, hasta).toMinutes());
    }
}
