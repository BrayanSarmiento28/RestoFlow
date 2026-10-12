package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.math.BigDecimal;

/**
 * Una línea del pedido: qué producto, cuántas unidades y a qué precio.
 * RN: el precio se copia del producto en el momento del pedido; si el precio cambia después,
 * los pedidos ya tomados no se alteran.
 */
public class DetallePedido {

    public static final int LONGITUD_MAXIMA_NOTA = 200;

    private final Long id;
    private final Long productoId;
    private final String productoNombre;
    private final int cantidad;
    private final BigDecimal precioUnitario;
    private final String nota;

    public DetallePedido(Long id, Long productoId, String productoNombre, int cantidad, BigDecimal precioUnitario,
                         String nota) {
        if (productoId == null) {
            throw new DomainException("Cada línea del pedido debe tener un producto");
        }
        if (cantidad <= 0) {
            throw new DomainException("La cantidad debe ser un número entero mayor que cero");
        }
        if (precioUnitario == null || precioUnitario.signum() <= 0) {
            throw new DomainException("El precio unitario debe ser mayor que cero");
        }
        String notaLimpia = nota == null || nota.isBlank() ? null : nota.trim();
        if (notaLimpia != null && notaLimpia.length() > LONGITUD_MAXIMA_NOTA) {
            throw new DomainException("La nota de cada producto admite máximo " + LONGITUD_MAXIMA_NOTA + " caracteres");
        }
        this.id = id;
        this.productoId = productoId;
        this.productoNombre = productoNombre;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.nota = notaLimpia;
    }

    /** Crea la línea tomando el precio vigente del producto. */
    public static DetallePedido de(Producto producto, int cantidad, String nota) {
        producto.validarDisponibleParaVenta();
        return new DetallePedido(null, producto.getId(), producto.getNombre(), cantidad, producto.getPrecio(), nota);
    }

    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() { return id; }
    public Long getProductoId() { return productoId; }
    public String getProductoNombre() { return productoNombre; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public String getNota() { return nota; }
}
