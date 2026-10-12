package com.udec.restoflow.infrastructure.adapter.in.web.dto;

import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.domain.model.Producto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** JSON de entrada y salida del menú (categorías y productos). */
public final class CatalogoDto {

    private CatalogoDto() {
    }

    // ---------- Entrada ----------

    public record CategoriaRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 60) String nombre) { }

    public record ProductoRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String nombre,
            @Size(max = 300, message = "La descripción admite máximo 300 caracteres") String descripcion,
            @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
            @Digits(integer = 10, fraction = 2, message = "El precio admite máximo 2 decimales") BigDecimal precio,
            @NotNull(message = "La categoría es obligatoria") Long categoriaId) { }

    public record DisponibilidadRequest(@NotNull(message = "Indique si el producto queda activo") Boolean activo) { }

    // ---------- Salida ----------

    public record CategoriaResponse(Long id, String nombre, boolean activa) {
        public static CategoriaResponse desde(Categoria c) {
            return new CategoriaResponse(c.getId(), c.getNombre(), c.isActiva());
        }
    }

    public record ProductoResponse(Long id, String nombre, String descripcion, BigDecimal precio, Long categoriaId,
                                   String categoriaNombre, boolean activo) {
        public static ProductoResponse desde(Producto p) {
            return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(),
                    p.getCategoriaId(), p.getCategoriaNombre(), p.isActivo());
        }
    }
}
