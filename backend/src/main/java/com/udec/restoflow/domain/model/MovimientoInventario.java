package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RF-12 · Registro inmutable de cada cambio de inventario: quién, cuándo, cuánto y por qué.
 * También es la fuente de datos de RestoFlow AI para calcular el consumo promedio (RF-10).
 */
public class MovimientoInventario {

    private final Long id;
    private final Long ingredienteId;
    private final TipoMovimiento tipo;
    private final BigDecimal cantidad;
    private final String motivo;
    private final Long usuarioId;
    private final Long pedidoId;
    private final LocalDateTime fecha;

    public MovimientoInventario(Long id, Long ingredienteId, TipoMovimiento tipo, BigDecimal cantidad, String motivo,
                                Long usuarioId, Long pedidoId, LocalDateTime fecha) {
        if (ingredienteId == null || tipo == null || fecha == null) {
            throw new DomainException("El movimiento debe indicar ingrediente, tipo y fecha");
        }
        if (cantidad == null || cantidad.signum() < 0) {
            throw new DomainException("La cantidad del movimiento no puede ser negativa");
        }
        this.id = id;
        this.ingredienteId = ingredienteId;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.motivo = motivo;
        this.usuarioId = usuarioId;
        this.pedidoId = pedidoId;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public Long getIngredienteId() { return ingredienteId; }
    public TipoMovimiento getTipo() { return tipo; }
    public BigDecimal getCantidad() { return cantidad; }
    public String getMotivo() { return motivo; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getPedidoId() { return pedidoId; }
    public LocalDateTime getFecha() { return fecha; }
}
