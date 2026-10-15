package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.GestionarRecetasUseCase;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.LineaRecetaRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.LineaRecetaResponse;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.RecetaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RF-06 · Receta de cada producto. Consultar: cualquier usuario · Definir: ADMIN (ver SecurityConfig). */
@Tag(name = "Recetas", description = "RF-06 Insumos que lleva cada producto · definir: ADMIN")
@RestController
@RequestMapping("/api/productos/{productoId}/receta")
public class RecetaController {

    private final GestionarRecetasUseCase recetas;

    public RecetaController(GestionarRecetasUseCase recetas) {
        this.recetas = recetas;
    }

    @Operation(summary = "Ver la receta de un producto", description = "Cantidad de cada insumo por unidad vendida.")
    @GetMapping
    public List<LineaRecetaResponse> receta(@PathVariable Long productoId) {
        return recetas.receta(productoId).stream().map(LineaRecetaResponse::desde).toList();
    }

    @Operation(summary = "Definir la receta",
            description = "Reemplaza la receta completa. Una lista vacía deja el producto sin descuento automático.")
    @PutMapping
    public List<LineaRecetaResponse> definir(@PathVariable Long productoId, @Valid @RequestBody RecetaRequest request) {
        return recetas.definir(productoId, request.lineas().stream().map(LineaRecetaRequest::aLinea).toList())
                .stream().map(LineaRecetaResponse::desde).toList();
    }
}
