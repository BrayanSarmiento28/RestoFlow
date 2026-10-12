package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.ComandoPedido;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.LineaPedido;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** RF-02 y RF-03 · Casos de uso de pedidos y cocina con dobles en memoria. */
class GestionarPedidosServiceTest {

    private DoblesPedidos.PedidosEnMemoria pedidos;
    private DoblesPedidos.ProductosEnMemoria productos;
    private Dobles.RelojManual reloj;
    private GestionarPedidosService servicio;
    private GestionarCocinaService cocina;
    private Long meseroId;
    private Long bandejaId;
    private Long limonadaId;

    @BeforeEach
    void preparar() {
        pedidos = new DoblesPedidos.PedidosEnMemoria();
        productos = new DoblesPedidos.ProductosEnMemoria();
        Dobles.UsuariosEnMemoria usuarios = new Dobles.UsuariosEnMemoria();
        reloj = new Dobles.RelojManual();
        servicio = new GestionarPedidosService(pedidos, productos, usuarios, reloj);
        cocina = new GestionarCocinaService(pedidos, reloj);

        meseroId = usuarios.guardar(Usuario.nuevo("Laura Gómez", "laura@resto.com", "hash", Rol.MESERO,
                LocalDateTime.now())).getId();
        bandejaId = productos.guardar(new Producto(null, "Bandeja paisa", null, new BigDecimal("32000"), 1L,
                "Platos fuertes", true)).getId();
        limonadaId = productos.guardar(new Producto(null, "Limonada de coco", null, new BigDecimal("9000"), 2L,
                "Bebidas", true)).getId();
    }

    private ComandoPedido pedidoMesa(int mesa) {
        return new ComandoPedido(mesa, List.of(new LineaPedido(bandejaId, 2, "Sin chicharrón"),
                new LineaPedido(limonadaId, 1, null)), null);
    }

    @Test
    @DisplayName("Registra el pedido con el mesero de la sesión, en Pendiente y con su total")
    void registraPedido() {
        Pedido pedido = servicio.registrar(pedidoMesa(5), meseroId);

        assertNotNull(pedido.getId());
        assertEquals("Laura Gómez", pedido.getMeseroNombre());
        assertEquals(EstadoCocina.PENDIENTE, pedido.getEstadoCocina());
        assertEquals(0, new BigDecimal("73000").compareTo(pedido.getTotal()));
        assertEquals(1, cocina.comandasPendientes().size());
    }

    @Test
    @DisplayName("Un producto inexistente responde 'no encontrado'")
    void productoInexistente() {
        ComandoPedido comando = new ComandoPedido(5, List.of(new LineaPedido(999L, 1, null)), null);
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.registrar(comando, meseroId));
    }

    @Test
    @DisplayName("Modificar un pedido Pendiente reemplaza sus líneas; en preparación ya no se puede")
    void modificaSoloPendiente() {
        Pedido pedido = servicio.registrar(pedidoMesa(5), meseroId);
        ComandoPedido soloLimonadas = new ComandoPedido(5, List.of(new LineaPedido(limonadaId, 3, null)), "Sin hielo");

        Pedido modificado = servicio.modificar(pedido.getId(), soloLimonadas);
        assertEquals(1, modificado.getDetalles().size());
        assertEquals(0, new BigDecimal("27000").compareTo(modificado.getTotal()));

        cocina.cambiarEstado(pedido.getId(), EstadoCocina.EN_PREPARACION);
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.modificar(pedido.getId(), soloLimonadas));
    }

    @Test
    @DisplayName("No se puede cambiar la mesa de un pedido al modificarlo")
    void noCambiaLaMesa() {
        Pedido pedido = servicio.registrar(pedidoMesa(5), meseroId);
        assertThrows(DomainException.class, () -> servicio.modificar(pedido.getId(), pedidoMesa(6)));
    }

    @Test
    @DisplayName("Un pedido cancelado desaparece de la cocina pero sigue en el historial")
    void cancelaPedido() {
        Pedido pedido = servicio.registrar(pedidoMesa(5), meseroId);
        servicio.cancelar(pedido.getId());

        assertEquals(0, cocina.comandasPendientes().size());
        assertEquals(1, servicio.consultar(null, null, EstadoPedido.CANCELADO).size());
    }

    @Test
    @DisplayName("Flujo completo: cocina prepara y entrega, el mesero registra la venta")
    void flujoCompleto() {
        Pedido pedido = servicio.registrar(pedidoMesa(5), meseroId);

        reloj.avanzarMinutos(2);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.EN_PREPARACION);
        reloj.avanzarMinutos(15);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.LISTO);
        reloj.avanzarMinutos(1);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.ENTREGADO);
        assertEquals(0, cocina.comandasPendientes().size());

        Pedido pagado = servicio.registrarVenta(pedido.getId());
        assertEquals(EstadoPedido.PAGADO, pagado.getEstadoPedido());
        assertNotNull(pagado.getFechaCierre());
    }

    @Test
    @DisplayName("La cocina atiende en orden de llegada")
    void cocinaEnOrdenDeLlegada() {
        Pedido primero = servicio.registrar(pedidoMesa(1), meseroId);
        reloj.avanzarMinutos(3);
        Pedido segundo = servicio.registrar(pedidoMesa(2), meseroId);

        List<Pedido> comandas = cocina.comandasPendientes();
        assertEquals(primero.getId(), comandas.get(0).getId());
        assertEquals(segundo.getId(), comandas.get(1).getId());
    }

    @Test
    @DisplayName("Se puede filtrar la consulta por mesa")
    void filtraPorMesa() {
        servicio.registrar(pedidoMesa(1), meseroId);
        servicio.registrar(pedidoMesa(2), meseroId);
        assertEquals(1, servicio.consultar(2, null, null).size());
    }
}
