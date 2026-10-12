package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas del pedido y de los estados de cocina (RF-02, RF-03), sin Spring ni base de datos. */
class PedidoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final Categoria PLATOS = new Categoria(1L, "Platos fuertes", true);
    private static final Usuario MESERO =
            new Usuario(7L, "Laura Gómez", "laura@resto.com", "hash", Rol.MESERO, true, AHORA);

    private static Producto producto(long id, String nombre, String precio) {
        return new Producto(id, nombre, null, new BigDecimal(precio), PLATOS.getId(), PLATOS.getNombre(), true);
    }

    private static Pedido pedidoDeEjemplo() {
        return Pedido.registrar(4, MESERO, List.of(
                DetallePedido.de(producto(1, "Bandeja paisa", "32000"), 2, "Sin chicharrón"),
                DetallePedido.de(producto(2, "Limonada de coco", "9000"), 3, null)), "Mesa de la ventana", AHORA);
    }

    @Test
    @DisplayName("Un pedido nuevo queda Pendiente, abierto y asociado al mesero")
    void pedidoNuevoQuedaPendiente() {
        Pedido pedido = pedidoDeEjemplo();
        assertEquals(EstadoCocina.PENDIENTE, pedido.getEstadoCocina());
        assertEquals(EstadoPedido.ABIERTO, pedido.getEstadoPedido());
        assertEquals(7L, pedido.getMeseroId());
        assertEquals(AHORA, pedido.getFechaCreacion());
    }

    @Test
    @DisplayName("El total es la suma de cantidad por precio de cada línea")
    void calculaElTotal() {
        // 2 × 32.000 + 3 × 9.000 = 91.000
        assertEquals(0, new BigDecimal("91000").compareTo(pedidoDeEjemplo().getTotal()));
    }

    @Test
    @DisplayName("No se registra un pedido sin productos ni sin mesa")
    void validaProductosYMesa() {
        assertThrows(DomainException.class, () -> Pedido.registrar(4, MESERO, List.of(), null, AHORA));
        List<DetallePedido> lineas = List.of(DetallePedido.de(producto(1, "Bandeja paisa", "32000"), 1, null));
        assertThrows(DomainException.class, () -> Pedido.registrar(0, MESERO, lineas, null, AHORA));
    }

    @Test
    @DisplayName("La cantidad de cada línea debe ser mayor que cero")
    void cantidadMayorQueCero() {
        assertThrows(DomainException.class, () -> DetallePedido.de(producto(1, "Bandeja paisa", "32000"), 0, null));
    }

    @Test
    @DisplayName("Un producto desactivado no se puede vender")
    void productoInactivoNoSeVende() {
        Producto inactivo = producto(1, "Bandeja paisa", "32000");
        inactivo.desactivar();
        assertThrows(OperacionNoPermitidaException.class, () -> DetallePedido.de(inactivo, 1, null));
    }

    @Test
    @DisplayName("La línea guarda el precio del momento; un cambio posterior de precio no la altera")
    void precioQuedaCongelado() {
        Producto bandeja = producto(1, "Bandeja paisa", "32000");
        DetallePedido linea = DetallePedido.de(bandeja, 1, null);
        bandeja.actualizar("Bandeja paisa", null, new BigDecimal("35000"), PLATOS);
        assertEquals(0, new BigDecimal("32000").compareTo(linea.getPrecioUnitario()));
    }

    @Test
    @DisplayName("Los estados avanzan en orden y guardan la hora de cada cambio")
    void estadosAvanzanEnOrden() {
        Pedido pedido = pedidoDeEjemplo();
        pedido.cambiarEstadoCocina(EstadoCocina.EN_PREPARACION, AHORA.plusMinutes(2));
        pedido.cambiarEstadoCocina(EstadoCocina.LISTO, AHORA.plusMinutes(20));
        pedido.cambiarEstadoCocina(EstadoCocina.ENTREGADO, AHORA.plusMinutes(22));

        assertEquals(EstadoCocina.ENTREGADO, pedido.getEstadoCocina());
        assertEquals(AHORA.plusMinutes(2), pedido.getFechaInicioPreparacion());
        assertEquals(AHORA.plusMinutes(20), pedido.getFechaListo());
        assertEquals(AHORA.plusMinutes(22), pedido.getFechaEntrega());
        assertFalse(pedido.estaPendienteEnCocina());
    }

    @Test
    @DisplayName("No se puede saltar ni retroceder un estado")
    void noSaltaNiRetrocede() {
        Pedido pedido = pedidoDeEjemplo();
        assertThrows(OperacionNoPermitidaException.class,
                () -> pedido.cambiarEstadoCocina(EstadoCocina.LISTO, AHORA));
        pedido.cambiarEstadoCocina(EstadoCocina.EN_PREPARACION, AHORA);
        assertThrows(OperacionNoPermitidaException.class,
                () -> pedido.cambiarEstadoCocina(EstadoCocina.PENDIENTE, AHORA));
    }

    @Test
    @DisplayName("Se puede modificar mientras está Pendiente y se recalcula el total")
    void modificaEnPendiente() {
        Pedido pedido = pedidoDeEjemplo();
        pedido.modificar(List.of(DetallePedido.de(producto(2, "Limonada de coco", "9000"), 1, null)), null);
        assertEquals(1, pedido.getDetalles().size());
        assertEquals(0, new BigDecimal("9000").compareTo(pedido.getTotal()));
    }

    @Test
    @DisplayName("No se puede modificar ni cancelar un pedido que ya está En preparación")
    void noModificaEnPreparacion() {
        Pedido pedido = pedidoDeEjemplo();
        pedido.cambiarEstadoCocina(EstadoCocina.EN_PREPARACION, AHORA);
        List<DetallePedido> nuevas = List.of(DetallePedido.de(producto(2, "Limonada de coco", "9000"), 1, null));

        assertThrows(OperacionNoPermitidaException.class, () -> pedido.modificar(nuevas, null));
        assertThrows(OperacionNoPermitidaException.class, () -> pedido.cancelar(AHORA));
    }

    @Test
    @DisplayName("Un pedido cancelado sale de la cocina y queda en el historial")
    void cancelaPedidoPendiente() {
        Pedido pedido = pedidoDeEjemplo();
        pedido.cancelar(AHORA.plusMinutes(1));
        assertEquals(EstadoPedido.CANCELADO, pedido.getEstadoPedido());
        assertNotNull(pedido.getFechaCierre());
        assertFalse(pedido.estaPendienteEnCocina());
        assertThrows(OperacionNoPermitidaException.class,
                () -> pedido.cambiarEstadoCocina(EstadoCocina.EN_PREPARACION, AHORA));
    }

    @Test
    @DisplayName("La venta solo se registra si el pedido fue Entregado; después queda cerrado")
    void ventaSoloDeEntregados() {
        Pedido pedido = pedidoDeEjemplo();
        assertThrows(OperacionNoPermitidaException.class, () -> pedido.registrarVenta(AHORA));

        pedido.cambiarEstadoCocina(EstadoCocina.EN_PREPARACION, AHORA);
        pedido.cambiarEstadoCocina(EstadoCocina.LISTO, AHORA);
        pedido.cambiarEstadoCocina(EstadoCocina.ENTREGADO, AHORA);
        pedido.registrarVenta(AHORA.plusMinutes(40));

        assertEquals(EstadoPedido.PAGADO, pedido.getEstadoPedido());
        assertEquals(AHORA.plusMinutes(40), pedido.getFechaCierre());
        assertThrows(OperacionNoPermitidaException.class, () -> pedido.cancelar(AHORA));
        assertThrows(OperacionNoPermitidaException.class, () -> pedido.registrarVenta(AHORA));
    }

    @Test
    @DisplayName("Un pedido abierto y no entregado aparece en la cocina")
    void apareceEnCocina() {
        assertTrue(pedidoDeEjemplo().estaPendienteEnCocina());
    }
}
