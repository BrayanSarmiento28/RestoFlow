package com.udec.restoflow.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** Tabla "receta_ingredientes": relación muchos a muchos entre productos e ingredientes, con la cantidad. */
@Entity
@Table(name = "receta_ingredientes",
        uniqueConstraints = @UniqueConstraint(name = "uq_receta_producto_ingrediente",
                columnNames = {"id_producto", "id_ingrediente"}))
public class RecetaIngredienteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_receta")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private ProductoEntity producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ingrediente", nullable = false)
    private IngredienteEntity ingrediente;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProductoEntity getProducto() { return producto; }
    public void setProducto(ProductoEntity producto) { this.producto = producto; }
    public IngredienteEntity getIngrediente() { return ingrediente; }
    public void setIngrediente(IngredienteEntity ingrediente) { this.ingrediente = ingrediente; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
}
