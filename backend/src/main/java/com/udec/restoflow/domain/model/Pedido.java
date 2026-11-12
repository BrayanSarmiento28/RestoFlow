package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private int numeroMesa;
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
    /** Número del pedido dentro de su día (#1, #2...): vuelve a empezar cada día. Null en pedidos anteriores. */
    private Integer numeroDelDia;
    /** Cómo se pagó (solo en pedidos Pagados). Null en ventas anteriores a este dato. */
    private MetodoPago metodoPago;
    /** Hora de la última adición (productos agregados con el pedido ya en cocina). Null si no tiene. */
    private LocalDateTime fechaAdicion;

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

    /**
     * RN: el pedido recibe su número del día al registrarse (el primero del día es el #1). Se asigna una sola vez;
     * después no cambia (es el que ve el administrador en el cierre del día).
     */
    public void asignarNumeroDelDia(int numero) {
        if (numero < 1) {
            throw new DomainException("El número del pedido en el día empieza en 1");
        }
        if (numeroDelDia != null && numeroDelDia != numero) {
            throw new OperacionNoPermitidaException("El pedido ya tiene su número del día (#" + numeroDelDia + ")");
        }
        this.numeroDelDia = numero;
    }

    /**
     * RN: los productos de un pedido solo se cambian mientras esté Pendiente. Ya enviado a cocina, lo único que se
     * puede cambiar es la mesa ({@link #cambiarMesa}).
     */
    public void modificar(List<DetallePedido> nuevosDetalles, String nuevasObservaciones) {
        validarAbierto("modificar");
        if (estadoCocina != EstadoCocina.PENDIENTE) {
            throw new OperacionNoPermitidaException("El pedido ya está " + estadoCocina.getDescripcion().toLowerCase()
                    + ": sus productos ya no se pueden cambiar. Si el cliente se movió de mesa, use \"Cambiar mesa\".");
        }
        this.detalles = validarDetalles(nuevosDetalles);
        this.observaciones = limpiarObservaciones(nuevasObservaciones);
    }

    /**
     * RN: el pedido se pasa a otra mesa (por ejemplo, el cliente se cambió de lugar). Se puede en cualquier momento
     * mientras esté abierto; que la mesa nueva esté libre lo revisa el servicio, que es quien ve los demás pedidos.
     */
    public void cambiarMesa(int nuevaMesa) {
        validarAbierto("cambiar de mesa");
        if (nuevaMesa <= 0) {
            throw new DomainException("El número de mesa no es válido");
        }
        if (nuevaMesa == numeroMesa) {
            throw new DomainException("El pedido ya está en la mesa " + numeroMesa);
        }
        this.numeroMesa = nuevaMesa;
    }

    /**
     * RN: el cliente pide algo más con el pedido ya en cocina. Los productos se suman al mismo pedido (y a su cuenta)
     * marcados como "adición" para que la cocina sepa qué falta. Si el pedido ya estaba Listo o Entregado, vuelve a
     * En preparación: la cocina debe preparar la adición y entregarla antes de cobrar. Mientras está Pendiente no
     * hace falta: se usa "Editar".
     */
    public void agregarAdicion(List<DetallePedido> adicion, LocalDateTime ahora) {
        validarAbierto("agregar productos a");
        if (estadoCocina == EstadoCocina.PENDIENTE) {
            throw new OperacionNoPermitidaException(
                    "El pedido aún no se empieza a preparar: agregue los productos con \"Editar\"");
        }
        if (adicion == null || adicion.isEmpty()) {
            throw new DomainException("Agregue al menos un producto a la adición");
        }
        if (adicion.stream().anyMatch(d -> !d.isAdicion())) {
            throw new DomainException("Los productos agregados después de enviar el pedido deben ser una adición");
        }
        List<DetallePedido> todas = new ArrayList<>(detalles);
        todas.addAll(adicion);
        this.detalles = List.copyOf(todas);
        this.fechaAdicion = ahora;
        if (estadoCocina == EstadoCocina.LISTO || estadoCocina == EstadoCocina.ENTREGADO) {
            this.estadoCocina = EstadoCocina.EN_PREPARACION;
            this.fechaListo = null;
            this.fechaEntrega = null;
        }
    }

    /** Al leer de la base de datos se recupera la hora de la última adición. */
    public void restaurarFechaAdicion(LocalDateTime fecha) {
        this.fechaAdicion = fecha;
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

    /**
     * RN: la venta solo se registra para pedidos entregados, y con su método de pago (efectivo o transferencia).
     * Después ya no se puede modificar ni cancelar, y la mesa queda libre.
     */
    public void registrarVenta(LocalDateTime ahora, MetodoPago metodo) {
        validarAbierto("registrar la venta de");
        if (metodo == null) {
            throw new DomainException("Elija el método de pago");
        }
        if (estadoCocina != EstadoCocina.ENTREGADO) {
            throw new OperacionNoPermitidaException("Solo se puede registrar la venta de un pedido Entregado; este está "
                    + estadoCocina.getDescripcion().toLowerCase());
        }
        this.estadoPedido = EstadoPedido.PAGADO;
        this.metodoPago = metodo;
        this.fechaCierre = ahora;
    }

    /** Al leer de la base de datos una venta ya registrada se recupera su método de pago. */
    public void asignarMetodoPago(MetodoPago metodo) {
        if (estadoPedido != EstadoPedido.PAGADO) {
            throw new OperacionNoPermitidaException("Solo un pedido Pagado tiene método de pago");
        }
        this.metodoPago = metodo;
    }

    // ---------- Consultas ----------

    /** Total = suma de cantidad × precio unitario de cada línea. */
    public BigDecimal getTotal() {
        return detalles.stream().map(DetallePedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** RN: una mesa está ocupada mientras tenga un pedido abierto (se libera al cobrarlo o cancelarlo). */
    public boolean ocupaMesa() {
        return estadoPedido == EstadoPedido.ABIERTO;
    }

    /** El pedido tiene productos agregados después de enviarlo a cocina. */
    public boolean tieneAdicion() {
        return fechaAdicion != null;
    }

    /**
     * Desde cuándo se mide la demora en cocina: la hora del pedido o, si tiene una adición, la hora de la adición
     * (la cocina tiene que preparar lo nuevo; no es una demora del pedido original).
     */
    public LocalDateTime getInicioEspera() {
        return fechaAdicion != null ? fechaAdicion : fechaCreacion;
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
    public Integer getNumeroDelDia() { return numeroDelDia; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public LocalDateTime getFechaAdicion() { return fechaAdicion; }
}
