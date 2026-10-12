package com.udec.restoflow.infrastructure.adapter.in.web.dto;

import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.ComandoPedido;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.LineaPedido;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** JSON de entrada y salida de pedidos y comandas de cocina. */
public final class PedidoDto {

    private PedidoDto() {
    }

    // ---------- Entrada ----------

    public record LineaRequest(
            @NotNull(message = "El producto es obligatorio") Long productoId,
            @Min(value = 1, message = "La cantidad debe ser mayor que cero")
            @Max(value = 99, message = "La cantidad máxima por línea es 99") int cantidad,
            @Size(max = 200, message = "La nota admite máximo 200 caracteres") String nota) { }

    public record PedidoRequest(
            @Min(value = 1, message = "El número de mesa es obligatorio")
            @Max(value = 999, message = "El número de mesa no es válido") int numeroMesa,
            @NotEmpty(message = "El pedido debe tener al menos un producto") List<@Valid LineaRequest> lineas,
            @Size(max = 300, message = "Las observaciones admiten máximo 300 caracteres") String observaciones) {

        public ComandoPedido aComando() {
            return new ComandoPedido(numeroMesa,
                    lineas.stream().map(l -> new LineaPedido(l.productoId(), l.cantidad(), l.nota())).toList(),
                    observaciones);
        }
    }

    public record CambioEstadoRequest(@NotNull(message = "El estado es obligatorio") EstadoCocina estado) { }

    // ---------- Salida ----------

    public record LineaResponse(Long id, Long productoId, String productoNombre, int cantidad, BigDecimal precioUnitario,
                                BigDecimal subtotal, String nota) {
        public static LineaResponse desde(DetallePedido d) {
            return new LineaResponse(d.getId(), d.getProductoId(), d.getProductoNombre(), d.getCantidad(),
                    d.getPrecioUnitario(), d.getSubtotal(), d.getNota());
        }
    }

    public record PedidoResponse(Long id, int numeroMesa, Long meseroId, String meseroNombre, List<LineaResponse> lineas,
                                 String observaciones, EstadoCocina estadoCocina, EstadoPedido estadoPedido,
                                 BigDecimal total, LocalDateTime fechaCreacion, LocalDateTime fechaInicioPreparacion,
                                 LocalDateTime fechaListo, LocalDateTime fechaEntrega, LocalDateTime fechaCierre) {
        public static PedidoResponse desde(Pedido p) {
            return new PedidoResponse(p.getId(), p.getNumeroMesa(), p.getMeseroId(), p.getMeseroNombre(),
                    p.getDetalles().stream().map(LineaResponse::desde).toList(), p.getObservaciones(),
                    p.getEstadoCocina(), p.getEstadoPedido(), p.getTotal(), p.getFechaCreacion(),
                    p.getFechaInicioPreparacion(), p.getFechaListo(), p.getFechaEntrega(), p.getFechaCierre());
        }
    }
}
