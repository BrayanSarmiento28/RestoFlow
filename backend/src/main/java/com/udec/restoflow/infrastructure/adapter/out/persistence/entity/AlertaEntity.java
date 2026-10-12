package com.udec.restoflow.infrastructure.adapter.out.persistence.entity;

import com.udec.restoflow.domain.model.PrioridadAlerta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Tabla "alertas": avisos de bajo inventario y recomendaciones de RestoFlow AI (RF-07, RF-11). */
@Entity
@Table(name = "alertas")
public class AlertaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ingrediente")
    private IngredienteEntity ingrediente;

    @Column(name = "mensaje", nullable = false, length = 300)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad", nullable = false, length = 10)
    private PrioridadAlerta prioridad;

    @Column(name = "atendida", nullable = false)
    private boolean atendida;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_atencion")
    private LocalDateTime fechaAtencion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public IngredienteEntity getIngrediente() { return ingrediente; }
    public void setIngrediente(IngredienteEntity ingrediente) { this.ingrediente = ingrediente; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public PrioridadAlerta getPrioridad() { return prioridad; }
    public void setPrioridad(PrioridadAlerta prioridad) { this.prioridad = prioridad; }
    public boolean isAtendida() { return atendida; }
    public void setAtendida(boolean atendida) { this.atendida = atendida; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaAtencion() { return fechaAtencion; }
    public void setFechaAtencion(LocalDateTime fechaAtencion) { this.fechaAtencion = fechaAtencion; }
}
