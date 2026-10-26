package com.udec.restoflow.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * RF-08 y RF-12 · Cálculos de ventas que comparten el panel del día y el historial.
 * <p>
 * Principio de responsabilidad única (SOLID - S): antes estos cálculos vivían dentro de ConsultarPanelService
 * y ConsultarHistorialService los tomaba prestados, así que un cambio en el panel podía romper el historial.
 * Ahora tienen su propio lugar en el dominio, sin dependencias, y se prueban solos (VentasTest).
 */
public final class Ventas {

    private Ventas() {
    }

    /** Suma del total de los pedidos (los pedidos ya vienen filtrados como ventas). */
    public static BigDecimal total(List<Pedido> pedidos) {
        return pedidos.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Ticket promedio en pesos, sin decimales. Sin ventas es 0 (nunca divide por cero). */
    public static BigDecimal ticketPromedio(BigDecimal total, int cantidad) {
        return cantidad == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(cantidad), 0, RoundingMode.HALF_UP);
    }
}
