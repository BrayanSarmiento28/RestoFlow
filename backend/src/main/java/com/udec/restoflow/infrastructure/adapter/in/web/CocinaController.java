package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.ConsultarStockCocinaUseCase;
import com.udec.restoflow.application.port.in.ConsultarStockCocinaUseCase.StockBajo;
import com.udec.restoflow.application.port.in.GestionarCocinaUseCase;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.PedidoDto.CambioEstadoRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.PedidoDto.PedidoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RF-03 y RF-04 · Pantalla de cocina. Solo COCINA y ADMIN. */
@Tag(name = "Cocina", description = "RF-03 Estados de preparación y RF-04 Comandas · COCINA y ADMIN")
@RestController
@RequestMapping("/api/cocina")
public class CocinaController {

    private final GestionarCocinaUseCase cocina;
    private final ConsultarStockCocinaUseCase stock;

    public CocinaController(GestionarCocinaUseCase cocina, ConsultarStockCocinaUseCase stock) {
        this.cocina = cocina;
        this.stock = stock;
    }

    @Operation(summary = "Comandas por atender",
            description = "Pedidos abiertos aún no entregados, del más antiguo al más reciente (orden de llegada).")
    @GetMapping("/comandas")
    public List<PedidoResponse> comandas() {
        return cocina.comandasPendientes().stream().map(PedidoResponse::desde).toList();
    }

    @Operation(summary = "Insumos que se están acabando en los platos de las comandas",
            description = "Para los productos indicados, los insumos de su receta que están en su mínimo o agotados. "
                    + "Lista vacía si todo alcanza.")
    @GetMapping("/stock")
    public List<StockBajo> stockBajo(@RequestParam(name = "productos", required = false) List<Long> productos) {
        return stock.stockBajo(productos == null ? List.of() : productos);
    }

    @Operation(summary = "Cambiar el estado de preparación",
            description = "Avanza en orden: PENDIENTE → EN_PREPARACION → LISTO → ENTREGADO. Saltar o retroceder responde 409.")
    @PatchMapping("/pedidos/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request) {
        return PedidoResponse.desde(cocina.cambiarEstado(id, request.estado()));
    }
}
