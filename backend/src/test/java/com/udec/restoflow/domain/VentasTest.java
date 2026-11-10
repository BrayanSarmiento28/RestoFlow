package com.udec.restoflow.domain;

import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Ventas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-08 y RF-12 · Cálculos de ventas compartidos por el panel y el historial (SOLID - S). */
class VentasTest {

    private static Pedido pedido(String... precios) {
        List<DetallePedido> lineas = java.util.Arrays.stream(precios)
                .map(p -> new DetallePedido(null, 1L, "Plato", 1, new BigDecimal(p), null)).toList();
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 30, 12, 0);
        return new Pedido(1L, 4, 1L, "Laura", lineas, null, EstadoCocina.ENTREGADO, EstadoPedido.PAGADO,
                ahora, null, null, null, ahora);
    }

    @Test
    @DisplayName("Suma el total de todos los pedidos")
    void total() {
        assertThat(Ventas.total(List.of(pedido("32000", "9000"), pedido("18000")))).isEqualByComparingTo("59000");
        assertThat(Ventas.total(List.of())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("El ticket promedio se redondea a pesos y sin ventas es 0")
    void ticketPromedio() {
        assertThat(Ventas.ticketPromedio(new BigDecimal("100000"), 3)).isEqualByComparingTo("33333");
        assertThat(Ventas.ticketPromedio(new BigDecimal("50000"), 3)).isEqualByComparingTo("16667");
        assertThat(Ventas.ticketPromedio(BigDecimal.ZERO, 0)).isEqualByComparingTo("0");
    }
}
