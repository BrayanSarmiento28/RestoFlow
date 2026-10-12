package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.GestionarPedidosUseCase;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.PedidoDto.PedidoRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.PedidoDto.PedidoResponse;
import com.udec.restoflow.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RF-02 · Pedidos. Registrar, modificar, cancelar y cobrar: MESERO o ADMIN · Consultar: también COCINA. */
@Tag(name = "Pedidos", description = "RF-02 Gestión de pedidos · MESERO y ADMIN")
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final GestionarPedidosUseCase pedidos;

    public PedidoController(GestionarPedidosUseCase pedidos) {
        this.pedidos = pedidos;
    }

    @Operation(summary = "Registrar pedido y enviarlo a cocina",
            description = "Queda en estado Pendiente, asociado al usuario de la sesión. Cada línea guarda el precio vigente.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse registrar(@Valid @RequestBody PedidoRequest request,
                                    @AuthenticationPrincipal UsuarioAutenticado actual) {
        return PedidoResponse.desde(pedidos.registrar(request.aComando(), actual.id()));
    }

    @Operation(summary = "Consultar pedidos", description = "Del más reciente al más antiguo. Todos los filtros son opcionales.")
    @GetMapping
    public List<PedidoResponse> consultar(
            @Parameter(description = "Número de mesa") @RequestParam(required = false) Integer mesa,
            @Parameter(description = "Estado de cocina") @RequestParam(required = false) EstadoCocina estadoCocina,
            @Parameter(description = "Estado del pedido") @RequestParam(required = false) EstadoPedido estadoPedido) {
        return pedidos.consultar(mesa, estadoCocina, estadoPedido).stream().map(PedidoResponse::desde).toList();
    }

    @Operation(summary = "Ver un pedido")
    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id) {
        return PedidoResponse.desde(pedidos.obtener(id));
    }

    @Operation(summary = "Modificar pedido",
            description = "Reemplaza productos, cantidades y observaciones. Solo si está Pendiente (si no, 409).")
    @PutMapping("/{id}")
    public PedidoResponse modificar(@PathVariable Long id, @Valid @RequestBody PedidoRequest request) {
        return PedidoResponse.desde(pedidos.modificar(id, request.aComando()));
    }

    @Operation(summary = "Cancelar pedido", description = "Solo si está Pendiente. Queda en el historial como Cancelado.")
    @PatchMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable Long id) {
        return PedidoResponse.desde(pedidos.cancelar(id));
    }

    @Operation(summary = "Registrar la venta",
            description = "Cierra como Pagado un pedido Entregado. No procesa pagos electrónicos.")
    @PatchMapping("/{id}/pagar")
    public PedidoResponse pagar(@PathVariable Long id) {
        return PedidoResponse.desde(pedidos.registrarVenta(id));
    }
}
