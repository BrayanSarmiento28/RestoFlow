package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.time.LocalDateTime;

/**
 * RF-07 / RF-11 · Aviso para el administrador o el personal de inventario
 * (por ejemplo: "Quedan 2 días de arroz"). La genera el sistema o RestoFlow AI.
 */
public class Alerta {

    private final Long id;
    private final Long ingredienteId;
    private final String mensaje;
    private final PrioridadAlerta prioridad;
    private boolean atendida;
    private final LocalDateTime fechaCreacion;
    private LocalDateTime fechaAtencion;

    public Alerta(Long id, Long ingredienteId, String mensaje, PrioridadAlerta prioridad, boolean atendida,
                  LocalDateTime fechaCreacion, LocalDateTime fechaAtencion) {
        if (mensaje == null || mensaje.isBlank()) {
            throw new DomainException("La alerta debe tener un mensaje");
        }
        if (prioridad == null) {
            throw new DomainException("La alerta debe tener una prioridad");
        }
        this.id = id;
        this.ingredienteId = ingredienteId;
        this.mensaje = mensaje.trim();
        this.prioridad = prioridad;
        this.atendida = atendida;
        this.fechaCreacion = fechaCreacion;
        this.fechaAtencion = fechaAtencion;
    }

    public static Alerta nueva(Long ingredienteId, String mensaje, PrioridadAlerta prioridad, LocalDateTime ahora) {
        return new Alerta(null, ingredienteId, mensaje, prioridad, false, ahora, null);
    }

    public void marcarAtendida(LocalDateTime ahora) {
        if (!atendida) {
            atendida = true;
            fechaAtencion = ahora;
        }
    }

    public Long getId() { return id; }
    public Long getIngredienteId() { return ingredienteId; }
    public String getMensaje() { return mensaje; }
    public PrioridadAlerta getPrioridad() { return prioridad; }
    public boolean isAtendida() { return atendida; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaAtencion() { return fechaAtencion; }
}
