package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.GestionarCatalogoUseCase;
import com.udec.restoflow.application.port.in.GestionarCatalogoUseCase.ComandoProducto;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.CatalogoDto.CategoriaRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.CatalogoDto.CategoriaResponse;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.CatalogoDto.DisponibilidadRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.CatalogoDto.ProductoRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.CatalogoDto.ProductoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/** Menú del restaurante. Consultar: cualquier usuario con sesión · Crear y editar: solo ADMIN. */
@Tag(name = "Menú", description = "Categorías y productos que se venden · crear y editar solo ADMIN")
@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final GestionarCatalogoUseCase catalogo;

    public CatalogoController(GestionarCatalogoUseCase catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(summary = "Listar categorías")
    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return catalogo.listarCategorias().stream().map(CategoriaResponse::desde).toList();
    }

    @Operation(summary = "Crear categoría", description = "Solo ADMIN. El nombre no se puede repetir (409).")
    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crearCategoria(@Valid @RequestBody CategoriaRequest request) {
        return CategoriaResponse.desde(catalogo.crearCategoria(request.nombre()));
    }

    @Operation(summary = "Listar productos",
            description = "Con soloActivos=true devuelve solo los disponibles para la venta (lo que usa el mesero).")
    @GetMapping("/productos")
    public List<ProductoResponse> productos(
            @Parameter(description = "true: solo productos disponibles") @RequestParam(defaultValue = "false") boolean soloActivos,
            @Parameter(description = "Filtrar por categoría") @RequestParam(required = false) Long categoriaId) {
        return catalogo.listarProductos(soloActivos, categoriaId).stream().map(ProductoResponse::desde).toList();
    }

    @Operation(summary = "Obtener un producto")
    @GetMapping("/productos/{id}")
    public ProductoResponse producto(@PathVariable Long id) {
        return ProductoResponse.desde(catalogo.obtenerProducto(id));
    }

    @Operation(summary = "Crear producto", description = "Solo ADMIN.")
    @PostMapping("/productos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crearProducto(@Valid @RequestBody ProductoRequest request) {
        return ProductoResponse.desde(catalogo.crearProducto(comando(request)));
    }

    @Operation(summary = "Editar producto",
            description = "Solo ADMIN. Cambiar el precio no altera los pedidos ya registrados.")
    @PutMapping("/productos/{id}")
    public ProductoResponse editarProducto(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return ProductoResponse.desde(catalogo.actualizarProducto(id, comando(request)));
    }

    @Operation(summary = "Activar o desactivar producto",
            description = "Solo ADMIN. Un producto desactivado no aparece para la venta; nunca se borra.")
    @PatchMapping("/productos/{id}/disponibilidad")
    public ProductoResponse disponibilidad(@PathVariable Long id, @Valid @RequestBody DisponibilidadRequest request) {
        return ProductoResponse.desde(catalogo.cambiarDisponibilidad(id, request.activo()));
    }

    private static ComandoProducto comando(ProductoRequest r) {
        return new ComandoProducto(r.nombre(), r.descripcion(), r.precio(), r.categoriaId());
    }
}
