package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarHistorialUseCase.ResumenVentas;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-12 · Historial de ventas y pedidos por rango de fechas. */
class ConsultarHistorialServiceTest {

    private DoblesPedidos.PedidosEnMemoria pedidos;
    private ConsultarHistorialService servicio;

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 19);

    @BeforeEach
    void preparar() {
        pedidos = new DoblesPedidos.PedidosEnMemoria();
        servicio = new ConsultarHistorialService(pedidos);
        pagado(LUNES.atTime(12, 30), "32000");
        pagado(LUNES.atTime(19, 0), "18000");
        pagado(LUNES.plusDays(1).atTime(13, 0), "45000");
        pagado(LUNES.plusDays(5).atTime(13, 0), "99000");                // fuera del rango consultado
        abierto(LUNES.atTime(20, 0));
    }

    private void pagado(LocalDateTime cierre, String precio) {
        pedidos.guardar(new Pedido(null, 4, 1L, "Laura", List.of(linea(precio)), null, EstadoCocina.ENTREGADO,
                EstadoPedido.PAGADO, cierre.minusMinutes(40), null, null, null, cierre));
    }

    private void abierto(LocalDateTime creado) {
        pedidos.guardar(new Pedido(null, 7, 1L, "Laura", List.of(linea("9000")), null, EstadoCocina.PENDIENTE,
                EstadoPedido.ABIERTO, creado, null, null, null, null));
    }

    private static DetallePedido linea(String precio) {
        return new DetallePedido(null, 1L, "Plato", 1, new BigDecimal(precio), null);
    }

    @Test
    @DisplayName("Suma las ventas del rango, agrupa por día y calcula el ticket promedio")
    void ventas() {
        ResumenVentas r = servicio.ventas(LUNES, LUNES.plusDays(1));

        assertThat(r.total()).isEqualByComparingTo("95000");
        assertThat(r.cantidad()).isEqualTo(3);
        assertThat(r.ticketPromedio()).isEqualByComparingTo("31667");       // 95000 / 3, redondeado
        assertThat(r.porDia()).hasSize(2);
        assertThat(r.porDia().get(0).dia()).isEqualTo(LUNES);
        assertThat(r.porDia().get(0).total()).isEqualByComparingTo("50000");
        assertThat(r.porDia().get(1).cantidad()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un rango sin ventas responde total 0 y promedio 0 (sin dividir por cero)")
    void sinVentas() {
        ResumenVentas r = servicio.ventas(LUNES.minusDays(10), LUNES.minusDays(9));
        assertThat(r.total()).isEqualByComparingTo("0");
        assertThat(r.ticketPromedio()).isEqualByComparingTo("0");
        assertThat(r.porDia()).isEmpty();
    }

    @Test
    @DisplayName("Los pedidos se filtran por fecha de creación y por estado")
    void pedidos() {
        assertThat(servicio.pedidos(LUNES, LUNES, null)).hasSize(3);
        assertThat(servicio.pedidos(LUNES, LUNES, EstadoPedido.ABIERTO)).singleElement()
                .satisfies(p -> assertThat(p.getNumeroMesa()).isEqualTo(7));
    }

    @Test
    @DisplayName("Valida las fechas: obligatorias, en orden y máximo 92 días")
    void fechasInvalidas() {
        assertThatThrownBy(() -> servicio.ventas(null, LUNES)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> servicio.ventas(LUNES, LUNES.minusDays(1))).isInstanceOf(DomainException.class)
                .hasMessageContaining("anterior");
        assertThatThrownBy(() -> servicio.pedidos(LUNES, LUNES.plusDays(92), null)).isInstanceOf(DomainException.class)
                .hasMessageContaining("92");
        assertThat(servicio.pedidos(LUNES, LUNES.plusDays(91), null)).isNotNull();
    }
}
