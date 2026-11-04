package com.udec.restoflow.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Tabla "registro_peticiones": cada petición al API (registro de actividad).
 * El índice por fecha hace rápidas la consulta de la pantalla y la limpieza diaria.
 */
@Entity
@Table(name = "registro_peticiones", indexes = {
        @Index(name = "ix_registro_fecha", columnList = "fecha"),
        @Index(name = "ix_registro_usuario_fecha", columnList = "usuario, fecha")})
public class RegistroPeticionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Long id;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "usuario", length = 150)
    private String usuario;

    @Column(name = "rol", length = 20)
    private String rol;

    @Column(name = "metodo", nullable = false, length = 10)
    private String metodo;

    @Column(name = "ruta", nullable = false, length = 500)
    private String ruta;

    @Column(name = "estado", nullable = false)
    private int estado;

    @Column(name = "duracion_ms", nullable = false)
    private long duracionMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }
    public String getRuta() { return ruta; }
    public void setRuta(String ruta) { this.ruta = ruta; }
    public int getEstado() { return estado; }
    public void setEstado(int estado) { this.estado = estado; }
    public long getDuracionMs() { return duracionMs; }
    public void setDuracionMs(long duracionMs) { this.duracionMs = duracionMs; }
}
