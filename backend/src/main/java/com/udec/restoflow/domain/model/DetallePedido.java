package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.math.BigDecimal;

/**
 * Una línea del pedido: qué producto, cuántas unidades y a qué precio.
 * RN: el precio se copia del producto en el momento del pedido; si el precio cambia después,
 * los pedidos ya tomados no se alteran.
 * Una línea es "adición" cuando se agregó después de enviar el pedido a cocina (el cliente pidió algo más).
 */
public class DetallePedido {

    public static final int LONGITUD_MAXIMA_NOTA = 200;

    private final Long id;
    private final Long productoId;
    private final String productoNombre;
    private final int cantidad;
    private final BigDecimal precioUnitario;
    private final String nota;
    private final boolean adicion;

    public DetallePedido(Long id, Long productoId, String productoNombre, int cantidad, BigDecimal precioUnitario,
                         String nota) {
        this(id, productoId, productoNombre, cantidad, precioUnitario, nota, false);
    }

    public DetallePedido(Long id, Long productoId, String productoNombre, int cantidad, BigDecimal precioUnitario,
                         String nota, boolean adicion) {
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
        this.adicion = adicion;
    }

    /** Crea la línea tomando el precio vigente del producto. */
    public static DetallePedido de(Producto producto, int cantidad, String nota) {
        producto.validarDisponibleParaVenta();
        return new DetallePedido(null, producto.getId(), producto.getNombre(), cantidad, producto.getPrecio(), nota);
    }

    /** Línea agregada a un pedido que ya está en cocina, con el precio vigente del producto. */
    public static DetallePedido adicionDe(Producto producto, int cantidad, String nota) {
        producto.validarDisponibleParaVenta();
        return new DetallePedido(null, producto.getId(), producto.getNombre(), cantidad, producto.getPrecio(), nota,
                true);
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
    public boolean isAdicion() { return adicion; }
}
