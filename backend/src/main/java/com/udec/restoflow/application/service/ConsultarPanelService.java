package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarPanelUseCase;
import com.udec.restoflow.application.port.out.AlertaRepositoryPort;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.Pedido;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * RF-08 · Arma el resumen del día para el panel del administrador.
 * Una venta es un pedido Pagado; su hora es la de cierre (cuando se cobró).
 */
@Transactional(readOnly = true)
public class ConsultarPanelService implements ConsultarPanelUseCase {

    private static final int TOP_PRODUCTOS = 5;

    private final PedidoRepositoryPort pedidos;
    private final IngredienteRepositoryPort ingredientes;
    private final AlertaRepositoryPort alertas;

    public ConsultarPanelService(PedidoRepositoryPort pedidos, IngredienteRepositoryPort ingredientes,
                                 AlertaRepositoryPort alertas) {
        this.pedidos = pedidos;
        this.ingredientes = ingredientes;
        this.alertas = alertas;
    }

    @Override
    public ResumenPanel resumen(LocalDate dia) {
        LocalDateTime inicio = dia.atStartOfDay();
        LocalDateTime fin = dia.plusDays(1).atStartOfDay();
        List<Pedido> ventas = pedidos.pagadosEntre(inicio, fin);
        BigDecimal total = sumar(ventas);
        BigDecimal ayer = sumar(pedidos.pagadosEntre(inicio.minusDays(1), inicio));
        int cancelados = pedidos.creadosEntre(inicio, fin, EstadoPedido.CANCELADO).size();

        List<InsumoBajo> bajos = ingredientes.listar(true).stream()
                .filter(Ingrediente::estaEnMinimo)
                .sorted(Comparator.comparing(ConsultarPanelService::proporcionDelMinimo))   // los más urgentes primero
                .map(i -> new InsumoBajo(i.getId(), i.getNombre(), i.getStockActual(), i.getStockMinimo(),
                        i.getUnidad().name()))
                .toList();

        return new ResumenPanel(dia, total, ventas.size(), promedio(total, ventas.size()), ayer,
                pedidos.buscar(null, null, EstadoPedido.ABIERTO).size(), pedidos.pendientesEnCocina().size(),
                cancelados, porHora(ventas), masVendidos(ventas), bajos, (int) alertas.contarPendientes());
    }

    /** Qué tan lleno está respecto a su mínimo (0 = agotado). Sirve para comparar insumos de distinta unidad. */
    private static BigDecimal proporcionDelMinimo(Ingrediente i) {
        return i.getStockMinimo().signum() == 0
                ? BigDecimal.ZERO
                : i.getStockActual().divide(i.getStockMinimo(), 4, RoundingMode.HALF_UP);
    }

    static BigDecimal sumar(List<Pedido> lista) {
        return lista.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal promedio(BigDecimal total, int cantidad) {
        return cantidad == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(cantidad), 0, RoundingMode.HALF_UP);
    }

    private static List<VentaPorHora> porHora(List<Pedido> ventas) {
        Map<Integer, List<Pedido>> grupos = new TreeMap<>();
        ventas.forEach(p -> grupos.computeIfAbsent(p.getFechaCierre().getHour(), h -> new ArrayList<>()).add(p));
        return grupos.entrySet().stream()
                .map(e -> new VentaPorHora(e.getKey(), sumar(e.getValue()), e.getValue().size()))
                .toList();
    }

    private static List<ProductoVendido> masVendidos(List<Pedido> ventas) {
        Map<Long, ProductoVendido> acumulado = new LinkedHashMap<>();
        for (Pedido pedido : ventas) {
            for (DetallePedido d : pedido.getDetalles()) {
                acumulado.merge(d.getProductoId(),
                        new ProductoVendido(d.getProductoId(), d.getProductoNombre(), d.getCantidad(), d.getSubtotal()),
                        (a, b) -> new ProductoVendido(a.productoId(), a.nombre(), a.cantidad() + b.cantidad(),
                                a.total().add(b.total())));
            }
        }
        return acumulado.values().stream()
                .sorted(Comparator.comparingInt(ProductoVendido::cantidad).reversed()
                        .thenComparing(ProductoVendido::total, Comparator.reverseOrder()))
                .limit(TOP_PRODUCTOS)
                .toList();
    }
}
