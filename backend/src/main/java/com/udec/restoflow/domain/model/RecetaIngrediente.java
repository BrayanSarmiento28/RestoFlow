package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.math.BigDecimal;

/**
 * RF-06 · Cuánto de un ingrediente lleva una unidad de un producto (receta).
 * Ejemplo: 1 "Bandeja paisa" lleva 200 g de arroz. Al vender 3 bandejas se descuentan 600 g.
 */
public class RecetaIngrediente {

    private final Long id;
    private final Long productoId;
    private final Long ingredienteId;
    private final BigDecimal cantidad;
    /** Datos del ingrediente solo para mostrar (pueden venir vacíos). */
    private final String ingredienteNombre;
    private final UnidadMedida unidad;

    public RecetaIngrediente(Long id, Long productoId, Long ingredienteId, BigDecimal cantidad) {
        this(id, productoId, ingredienteId, cantidad, null, null);
    }

    public RecetaIngrediente(Long id, Long productoId, Long ingredienteId, BigDecimal cantidad,
                             String ingredienteNombre, UnidadMedida unidad) {
        if (productoId == null || ingredienteId == null) {
            throw new DomainException("La receta debe indicar el producto y el ingrediente");
        }
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException("La cantidad del ingrediente en la receta debe ser mayor que cero");
        }
        this.id = id;
        this.productoId = productoId;
        this.ingredienteId = ingredienteId;
        this.cantidad = cantidad;
        this.ingredienteNombre = ingredienteNombre;
        this.unidad = unidad;
    }

    /** Cantidad total del ingrediente que se consume al vender varias unidades del producto. */
    public BigDecimal consumoPara(int unidadesVendidas) {
        return cantidad.multiply(BigDecimal.valueOf(unidadesVendidas));
    }

    public Long getId() { return id; }
    public Long getProductoId() { return productoId; }
    public Long getIngredienteId() { return ingredienteId; }
    public BigDecimal getCantidad() { return cantidad; }
    public String getIngredienteNombre() { return ingredienteNombre; }
    public UnidadMedida getUnidad() { return unidad; }
}
