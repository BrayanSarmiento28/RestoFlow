package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** RF-12 · Historial de pedidos y ventas por rango de fechas (los movimientos están en inventario). */
public interface ConsultarHistorialUseCase {

    /** Pedidos creados entre las dos fechas (incluidas), del más reciente al más antiguo. */
    List<Pedido> pedidos(LocalDate desde, LocalDate hasta, EstadoPedido estado);

    /** Ventas (pedidos pagados) cerradas entre las dos fechas, con el total por día. */
    ResumenVentas ventas(LocalDate desde, LocalDate hasta);

    record ResumenVentas(LocalDate desde, LocalDate hasta, BigDecimal total, int cantidad, BigDecimal ticketPromedio,
                         List<VentaPorDia> porDia, List<Pedido> ventas) { }

    record VentaPorDia(LocalDate dia, BigDecimal total, int cantidad) { }
}
