package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-02 / RF-03 · Pedido de una mesa. Es la entidad central del restaurante.
 * <p>
 * Tiene dos estados independientes:
 * <ul>
 *   <li><b>estadoCocina</b>: cómo va la preparación (Pendiente → En preparación → Listo → Entregado).</li>
 *   <li><b>estadoPedido</b>: la parte comercial (Abierto → Pagado, o Cancelado).</li>
 * </ul>
 * Todas las reglas de qué se puede hacer y cuándo están aquí, no en los controladores.
 */
public class Pedido {

    public static final int LONGITUD_MAXIMA_OBSERVACIONES = 300;

    private final Long id;
    private final int numeroMesa;
    private final Long meseroId;
    private final String meseroNombre;
    private List<DetallePedido> detalles;
    private String observaciones;
    private EstadoCocina estadoCocina;
    private EstadoPedido estadoPedido;
    private final LocalDateTime fechaCreacion;
    private LocalDateTime fechaInicioPreparacion;
    private LocalDateTime fechaListo;
    private LocalDateTime fechaEntrega;
    private LocalDateTime fechaCierre;

    /** Reconstruye un pedido existente (lo usa la capa de persistencia). */
    public Pedido(Long id, int numeroMesa, Long meseroId, String meseroNombre, List<DetallePedido> detalles,
                  String observaciones, EstadoCocina estadoCocina, EstadoPedido estadoPedido,
                  LocalDateTime fechaCreacion, LocalDateTime fechaInicioPreparacion, LocalDateTime fechaListo,
                  LocalDateTime fechaEntrega, LocalDateTime fechaCierre) {
        if (numeroMesa <= 0) {
            throw new DomainException("El pedido debe tener un número de mesa válido");
        }
        if (meseroId == null) {
            throw new DomainException("El pedido debe quedar asociado al usuario que lo registra");
        }
        this.id = id;
        this.numeroMesa = numeroMesa;
        this.meseroId = meseroId;
        this.meseroNombre = meseroNombre;
        this.detalles = validarDetalles(detalles);
        this.observaciones = limpiarObservaciones(observaciones);
        this.estadoCocina = estadoCocina;
        this.estadoPedido = estadoPedido;
        this.fechaCreacion = fechaCreacion;
        this.fechaInicioPreparacion = fechaInicioPreparacion;
        this.fechaListo = fechaListo;
        this.fechaEntrega = fechaEntrega;
        this.fechaCierre = fechaCierre;
    }

    /**
     * Registra y envía a cocina un pedido nuevo (HU Registrar pedido, Agregar productos, Enviar a cocina).
     * Empieza en Pendiente y Abierto.
     */
    public static Pedido registrar(int numeroMesa, Usuario mesero, List<DetallePedido> detalles,
                                   String observaciones, LocalDateTime ahora) {
        return new Pedido(null, numeroMesa, mesero.getId(), mesero.getNombre(), detalles, observaciones,
                EstadoCocina.PENDIENTE, EstadoPedido.ABIERTO, ahora, null, null, null, null);
    }

    // ---------- Reglas de negocio ----------

    /** RN: un pedido solo puede modificarse mientras esté Pendiente. */
    public void modificar(List<DetallePedido> nuevosDetalles, String nuevasObservaciones) {
        validarAbierto("modificar");
        if (estadoCocina != EstadoCocina.PENDIENTE) {
            throw new OperacionNoPermitidaException("El pedido ya está " + estadoCocina.getDescripcion().toLowerCase()
                    + " y no se puede modificar. Registre un pedido nuevo.");
        }
        this.detalles = validarDetalles(nuevosDetalles);
        this.observaciones = limpiarObservaciones(nuevasObservaciones);
    }

    /** RN: solo se cancelan pedidos que no han iniciado preparación. Quedan en el historial. */
    public void cancelar(LocalDateTime ahora) {
        validarAbierto("cancelar");
        if (estadoCocina != EstadoCocina.PENDIENTE) {
            throw new OperacionNoPermitidaException("Solo se puede cancelar un pedido Pendiente; este ya está "
                    + estadoCocina.getDescripcion().toLowerCase());
        }
        this.estadoPedido = EstadoPedido.CANCELADO;
        this.fechaCierre = ahora;
    }

    /**
     * RN: los estados de cocina avanzan en orden, sin saltos ni retrocesos.
     * Se guarda la hora de cada cambio para medir los tiempos de preparación.
     */
    public void cambiarEstadoCocina(EstadoCocina nuevo, LocalDateTime ahora) {
        if (nuevo == null) {
            throw new DomainException("El nuevo estado es obligatorio");
        }
        validarAbierto("cambiar el estado de");
        EstadoCocina permitido = estadoCocina.siguiente();
        if (nuevo != permitido) {
            String mensaje = permitido == null
                    ? "El pedido ya fue entregado; no tiene más estados de cocina"
                    : "Un pedido " + estadoCocina.getDescripcion() + " solo puede pasar a " + permitido.getDescripcion();
            throw new OperacionNoPermitidaException(mensaje);
        }
        this.estadoCocina = nuevo;
        switch (nuevo) {
            case EN_PREPARACION -> this.fechaInicioPreparacion = ahora;
            case LISTO -> this.fechaListo = ahora;
            case ENTREGADO -> this.fechaEntrega = ahora;
            default -> { }
        }
    }

    /** RN: la venta solo se registra para pedidos entregados. Después ya no se puede modificar ni cancelar. */
    public void registrarVenta(LocalDateTime ahora) {
        validarAbierto("registrar la venta de");
        if (estadoCocina != EstadoCocina.ENTREGADO) {
            throw new OperacionNoPermitidaException("Solo se puede registrar la venta de un pedido Entregado; este está "
                    + estadoCocina.getDescripcion().toLowerCase());
        }
        this.estadoPedido = EstadoPedido.PAGADO;
        this.fechaCierre = ahora;
    }

    // ---------- Consultas ----------

    /** Total = suma de cantidad × precio unitario de cada línea. */
    public BigDecimal getTotal() {
        return detalles.stream().map(DetallePedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** RF-04 · La cocina ve los pedidos abiertos que todavía no se han entregado. */
    public boolean estaPendienteEnCocina() {
        return estadoPedido == EstadoPedido.ABIERTO && estadoCocina != EstadoCocina.ENTREGADO;
    }

    // ---------- Validaciones internas ----------

    private void validarAbierto(String accion) {
        if (estadoPedido != EstadoPedido.ABIERTO) {
            throw new OperacionNoPermitidaException("No se puede " + accion + " un pedido "
                    + estadoPedido.name().toLowerCase());
        }
    }

    /** RN: un pedido solo se envía a cocina si tiene al menos un producto. */
    private static List<DetallePedido> validarDetalles(List<DetallePedido> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            throw new DomainException("El pedido debe tener al menos un producto");
        }
        return List.copyOf(detalles);
    }

    private static String limpiarObservaciones(String observaciones) {
        if (observaciones == null || observaciones.isBlank()) {
            return null;
        }
        String limpias = observaciones.trim();
        if (limpias.length() > LONGITUD_MAXIMA_OBSERVACIONES) {
            throw new DomainException("Las observaciones admiten máximo " + LONGITUD_MAXIMA_OBSERVACIONES + " caracteres");
        }
        return limpias;
    }

    public Long getId() { return id; }
    public int getNumeroMesa() { return numeroMesa; }
    public Long getMeseroId() { return meseroId; }
    public String getMeseroNombre() { return meseroNombre; }
    public List<DetallePedido> getDetalles() { return detalles; }
    public String getObservaciones() { return observaciones; }
    public EstadoCocina getEstadoCocina() { return estadoCocina; }
    public EstadoPedido getEstadoPedido() { return estadoPedido; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaInicioPreparacion() { return fechaInicioPreparacion; }
    public LocalDateTime getFechaListo() { return fechaListo; }
    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
}
