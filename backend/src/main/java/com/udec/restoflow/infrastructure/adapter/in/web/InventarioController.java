package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.GestionarInventarioUseCase;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.AjusteRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.EstadoRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.IngredienteRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.IngredienteResponse;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.MovimientoRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.MovimientoResponse;
import com.udec.restoflow.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/** RF-05 · Insumos y movimientos de inventario. INVENTARIO y ADMIN. */
@Tag(name = "Inventario", description = "RF-05 Existencias, entradas, salidas y ajustes · INVENTARIO y ADMIN")
@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final GestionarInventarioUseCase inventario;

    public InventarioController(GestionarInventarioUseCase inventario) {
        this.inventario = inventario;
    }

    @Operation(summary = "Listar insumos", description = "Ordenados por nombre. Cada uno indica si está en su mínimo.")
    @GetMapping("/ingredientes")
    public List<IngredienteResponse> listar(
            @Parameter(description = "true: solo los activos") @RequestParam(defaultValue = "false") boolean soloActivos) {
        return inventario.listar(soloActivos).stream().map(IngredienteResponse::desde).toList();
    }

    @Operation(summary = "Ver un insumo")
    @GetMapping("/ingredientes/{id}")
    public IngredienteResponse obtener(@PathVariable Long id) {
        return IngredienteResponse.desde(inventario.obtener(id));
    }

    @Operation(summary = "Crear insumo", description = "Si trae stock inicial, queda registrado como una entrada.")
    @PostMapping("/ingredientes")
    @ResponseStatus(HttpStatus.CREATED)
    public IngredienteResponse crear(@Valid @RequestBody IngredienteRequest request,
                                     @AuthenticationPrincipal UsuarioAutenticado actual) {
        return IngredienteResponse.desde(inventario.crear(request.aComando(), actual.id()));
    }

    @Operation(summary = "Editar insumo", description = "Nombre, unidad y stock mínimo. El stock se cambia con movimientos.")
    @PutMapping("/ingredientes/{id}")
    public IngredienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody IngredienteRequest request) {
        return IngredienteResponse.desde(inventario.actualizar(id, request.aComando()));
    }

    @Operation(summary = "Activar o desactivar insumo", description = "Nunca se borra, para conservar su historial.")
    @PatchMapping("/ingredientes/{id}/estado")
    public IngredienteResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest request) {
        return IngredienteResponse.desde(inventario.cambiarEstado(id, request.activo()));
    }

    @Operation(summary = "Registrar entrada", description = "Compra o ingreso de mercancía: suma al stock.")
    @PostMapping("/ingredientes/{id}/entradas")
    public IngredienteResponse entrada(@PathVariable Long id, @Valid @RequestBody MovimientoRequest request,
                                       @AuthenticationPrincipal UsuarioAutenticado actual) {
        return IngredienteResponse.desde(inventario.registrarEntrada(id, request.cantidad(), request.motivo(),
                actual.id()));
    }

    @Operation(summary = "Registrar salida", description = "Merma, daño o vencimiento. El motivo es obligatorio. "
            + "No se puede sacar más de lo que hay (409).")
    @PostMapping("/ingredientes/{id}/salidas")
    public IngredienteResponse salida(@PathVariable Long id, @Valid @RequestBody MovimientoRequest request,
                                      @AuthenticationPrincipal UsuarioAutenticado actual) {
        return IngredienteResponse.desde(inventario.registrarSalida(id, request.cantidad(), request.motivo(),
                actual.id()));
    }

    @Operation(summary = "Ajustar por conteo físico", description = "Deja el stock en la cantidad contada.")
    @PostMapping("/ingredientes/{id}/ajustes")
    public IngredienteResponse ajuste(@PathVariable Long id, @Valid @RequestBody AjusteRequest request,
                                      @AuthenticationPrincipal UsuarioAutenticado actual) {
        return IngredienteResponse.desde(inventario.ajustar(id, request.stockContado(), request.motivo(), actual.id()));
    }

    @Operation(summary = "Historial de movimientos (RF-12)",
            description = "Entradas, salidas (incluidas las ventas) y ajustes. Filtros opcionales; fechas AAAA-MM-DD.")
    @GetMapping("/movimientos")
    public List<MovimientoResponse> movimientos(
            @RequestParam(required = false) Long ingredienteId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return inventario.movimientos(ingredienteId, tipo, desde, hasta).stream().map(MovimientoResponse::desde).toList();
    }
}
