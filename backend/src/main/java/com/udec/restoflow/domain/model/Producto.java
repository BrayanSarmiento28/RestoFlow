package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;

import java.math.BigDecimal;

/**
 * Plato o bebida que se vende en el restaurante.
 * Un producto no se borra: se desactiva, para que los pedidos antiguos conserven su información.
 */
public class Producto {

    private final Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Long categoriaId;
    private String categoriaNombre;
    private boolean activo;

    public Producto(Long id, String nombre, String descripcion, BigDecimal precio, Long categoriaId,
                    String categoriaNombre, boolean activo) {
        this.id = id;
        this.nombre = validarNombre(nombre);
        this.descripcion = descripcion == null ? null : descripcion.trim();
        this.precio = validarPrecio(precio);
        this.categoriaId = categoriaRequerida(categoriaId);
        this.categoriaNombre = categoriaNombre;
        this.activo = activo;
    }

    public static Producto nuevo(String nombre, String descripcion, BigDecimal precio, Categoria categoria) {
        return new Producto(null, nombre, descripcion, precio, categoria.getId(), categoria.getNombre(), true);
    }

    // ---------- Reglas de negocio ----------

    /** RN: los productos desactivados no aparecen para la venta. */
    public void validarDisponibleParaVenta() {
        if (!activo) {
            throw new OperacionNoPermitidaException("El producto " + nombre + " no está disponible para la venta");
        }
    }

    public void actualizar(String nombre, String descripcion, BigDecimal precio, Categoria categoria) {
        this.nombre = validarNombre(nombre);
        this.descripcion = descripcion == null ? null : descripcion.trim();
        this.precio = validarPrecio(precio);
        this.categoriaId = categoriaRequerida(categoria.getId());
        this.categoriaNombre = categoria.getNombre();
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    // ---------- Validaciones internas ----------

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.trim().length() < 3) {
            throw new DomainException("El nombre del producto debe tener al menos 3 caracteres");
        }
        return nombre.trim();
    }

    private static BigDecimal validarPrecio(BigDecimal precio) {
        if (precio == null || precio.signum() <= 0) {
            throw new DomainException("El precio debe ser mayor que cero");
        }
        return precio;
    }

    private static Long categoriaRequerida(Long categoriaId) {
        if (categoriaId == null) {
            throw new DomainException("El producto debe pertenecer a una categoría");
        }
        return categoriaId;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public Long getCategoriaId() { return categoriaId; }
    public String getCategoriaNombre() { return categoriaNombre; }
    public boolean isActivo() { return activo; }
}
