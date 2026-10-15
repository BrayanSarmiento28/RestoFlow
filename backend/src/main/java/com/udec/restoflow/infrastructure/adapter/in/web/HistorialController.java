package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.ConsultarHistorialUseCase;
import com.udec.restoflow.application.port.in.ConsultarHistorialUseCase.ResumenVentas;
import com.udec.restoflow.application.port.in.ConsultarHistorialUseCase.VentaPorDia;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.PedidoDto.PedidoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** RF-12 · Historial de pedidos y ventas. Solo ADMIN (los movimientos están en /api/inventario/movimientos). */
@Tag(name = "Historial", description = "RF-12 Historial de pedidos y ventas · ADMIN")
@RestController
@RequestMapping("/api/historial")
public class HistorialController {

    private final ConsultarHistorialUseCase historial;

    public HistorialController(ConsultarHistorialUseCase historial) {
        this.historial = historial;
    }

    @Operation(summary = "Pedidos por rango de fechas",
            description = "Por fecha de creación, ambas fechas incluidas (AAAA-MM-DD). Máximo 92 días.")
    @GetMapping("/pedidos")
    public List<PedidoResponse> pedidos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoPedido estado) {
        return historial.pedidos(desde, hasta, estado).stream().map(PedidoResponse::desde).toList();
    }

    @Operation(summary = "Ventas por rango de fechas",
            description = "Pedidos pagados según la fecha de cobro, con total, ticket promedio y total por día.")
    @GetMapping("/ventas")
    public VentasResponse ventas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        ResumenVentas r = historial.ventas(desde, hasta);
        return new VentasResponse(r.desde(), r.hasta(), r.total(), r.cantidad(), r.ticketPromedio(), r.porDia(),
                r.ventas().stream().map(PedidoResponse::desde).toList());
    }

    public record VentasResponse(LocalDate desde, LocalDate hasta, BigDecimal total, int cantidad,
                                 BigDecimal ticketPromedio, List<VentaPorDia> porDia, List<PedidoResponse> ventas) { }
}
