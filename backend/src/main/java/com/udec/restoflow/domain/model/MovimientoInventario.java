package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RF-12 · Registro inmutable de cada cambio de inventario: quién, cuándo, cuánto y por qué.
 * También es la fuente de datos de RestoFlow AI para calcular el consumo promedio (RF-10).
 * En un AJUSTE, la cantidad es el valor contado (el stock quedó en esa cantidad).
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
    /** Datos solo para mostrar en el historial (pueden venir vacíos). */
    private final String ingredienteNombre;
    private final UnidadMedida unidad;
    private final String usuarioNombre;

    public MovimientoInventario(Long id, Long ingredienteId, TipoMovimiento tipo, BigDecimal cantidad, String motivo,
                                Long usuarioId, Long pedidoId, LocalDateTime fecha) {
        this(id, ingredienteId, tipo, cantidad, motivo, usuarioId, pedidoId, fecha, null, null, null);
    }

    public MovimientoInventario(Long id, Long ingredienteId, TipoMovimiento tipo, BigDecimal cantidad, String motivo,
                                Long usuarioId, Long pedidoId, LocalDateTime fecha, String ingredienteNombre,
                                UnidadMedida unidad, String usuarioNombre) {
        if (ingredienteId == null || tipo == null || fecha == null) {
            throw new DomainException("El movimiento debe indicar ingrediente, tipo y fecha");
        }
        if (cantidad == null || cantidad.signum() < 0) {
            throw new DomainException("La cantidad del movimiento no puede ser negativa");
        }
        if (motivo != null && motivo.trim().length() > 200) {
            throw new DomainException("El motivo admite máximo 200 caracteres");
        }
        this.id = id;
        this.ingredienteId = ingredienteId;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.motivo = motivo == null || motivo.isBlank() ? null : motivo.trim();
        this.usuarioId = usuarioId;
        this.pedidoId = pedidoId;
        this.fecha = fecha;
        this.ingredienteNombre = ingredienteNombre;
        this.unidad = unidad;
        this.usuarioNombre = usuarioNombre;
    }

    /** Movimiento nuevo hecho por una persona (usuarioId) o por el sistema (usuarioId null). */
    public static MovimientoInventario registrar(Long ingredienteId, TipoMovimiento tipo, BigDecimal cantidad,
                                                 String motivo, Long usuarioId, Long pedidoId, LocalDateTime ahora) {
        return new MovimientoInventario(null, ingredienteId, tipo, cantidad, motivo, usuarioId, pedidoId, ahora);
    }

    public Long getId() { return id; }
    public Long getIngredienteId() { return ingredienteId; }
    public TipoMovimiento getTipo() { return tipo; }
    public BigDecimal getCantidad() { return cantidad; }
    public String getMotivo() { return motivo; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getPedidoId() { return pedidoId; }
    public LocalDateTime getFecha() { return fecha; }
    public String getIngredienteNombre() { return ingredienteNombre; }
    public UnidadMedida getUnidad() { return unidad; }
    public String getUsuarioNombre() { return usuarioNombre; }
}
