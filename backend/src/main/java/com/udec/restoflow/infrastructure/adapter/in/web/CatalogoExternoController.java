package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.BuscarProductoExternoUseCase;
import com.udec.restoflow.domain.model.ProductoExterno;
import com.udec.restoflow.domain.model.UnidadMedida;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-05 · Datos de un insumo por código de barras (API externa Open Food Facts). INVENTARIO y ADMIN. */
@Tag(name = "Catálogo externo", description = "API externa Open Food Facts: buscar un producto por código de barras · INVENTARIO y ADMIN")
@RestController
@RequestMapping("/api/inventario/catalogo-externo")
public class CatalogoExternoController {

    private final BuscarProductoExternoUseCase catalogo;

    public CatalogoExternoController(BuscarProductoExternoUseCase catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(summary = "Buscar por código de barras",
            description = "Consulta Open Food Facts y devuelve el nombre, la marca, la foto y la unidad sugerida para "
                    + "crear el insumo. 400 si el código no es válido, 404 si no existe, 503 si el servicio no responde. "
                    + "Ejemplo: 4006381333931")
    @GetMapping("/{codigo}")
    public ProductoExternoResponse buscar(@PathVariable String codigo) {
        return ProductoExternoResponse.desde(catalogo.buscarPorCodigo(codigo));
    }

    public record ProductoExternoResponse(String codigoBarras, String nombre, String marca, String cantidad,
                                          String imagenUrl, String fuente, String nombreSugerido,
                                          UnidadMedida unidadSugerida) {
        static ProductoExternoResponse desde(ProductoExterno p) {
            return new ProductoExternoResponse(p.codigoBarras(), p.nombre(), p.marca(), p.cantidad(), p.imagenUrl(),
                    p.fuente(), p.nombreSugerido(), p.unidadSugerida());
        }
    }
}
