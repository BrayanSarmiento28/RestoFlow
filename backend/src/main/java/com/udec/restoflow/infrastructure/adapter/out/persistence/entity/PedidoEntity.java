package com.udec.restoflow.infrastructure.adapter.out.persistence.entity;

import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tabla "pedidos". Relaciones: cada pedido lo registra un usuario (mesero) y tiene varias líneas
 * en "detalle_pedidos" (se guardan y borran junto con el pedido: cascade + orphanRemoval).
 */
@Entity
@Table(name = "pedidos", indexes = @Index(name = "ix_pedidos_estados", columnList = "estado_pedido, estado_cocina"))
public class PedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long id;

    @Column(name = "numero_mesa", nullable = false)
    private int numeroMesa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private UsuarioEntity mesero;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DetallePedidoEntity> detalles = new ArrayList<>();

    @Column(name = "observaciones", length = 300)
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cocina", nullable = false, length = 20)
    private EstadoCocina estadoCocina;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pedido", nullable = false, length = 20)
    private EstadoPedido estadoPedido;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_inicio_preparacion")
    private LocalDateTime fechaInicioPreparacion;

    @Column(name = "fecha_listo")
    private LocalDateTime fechaListo;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    /** Mantiene sincronizados los dos lados de la relación pedido ↔ detalle. */
    public void agregarDetalle(DetallePedidoEntity detalle) {
        detalle.setPedido(this);
        detalles.add(detalle);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getNumeroMesa() { return numeroMesa; }
    public void setNumeroMesa(int numeroMesa) { this.numeroMesa = numeroMesa; }
    public UsuarioEntity getMesero() { return mesero; }
    public void setMesero(UsuarioEntity mesero) { this.mesero = mesero; }
    public List<DetallePedidoEntity> getDetalles() { return detalles; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public EstadoCocina getEstadoCocina() { return estadoCocina; }
    public void setEstadoCocina(EstadoCocina estadoCocina) { this.estadoCocina = estadoCocina; }
    public EstadoPedido getEstadoPedido() { return estadoPedido; }
    public void setEstadoPedido(EstadoPedido estadoPedido) { this.estadoPedido = estadoPedido; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaInicioPreparacion() { return fechaInicioPreparacion; }
    public void setFechaInicioPreparacion(LocalDateTime v) { this.fechaInicioPreparacion = v; }
    public LocalDateTime getFechaListo() { return fechaListo; }
    public void setFechaListo(LocalDateTime fechaListo) { this.fechaListo = fechaListo; }
    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDateTime fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
}
