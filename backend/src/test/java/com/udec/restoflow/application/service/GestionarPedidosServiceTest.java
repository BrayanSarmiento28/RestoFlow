package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.ComandoPedido;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.LineaPedido;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.MesaOcupada;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.MesaOcupadaException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.MetodoPago;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        servicio = new GestionarPedidosService(pedidos, productos, usuarios, pedido -> List.of(), reloj);
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
    @DisplayName("Una mesa con un pedido abierto está ocupada: no recibe otro pedido (409 con el pedido que la ocupa)")
    void mesaOcupada() {
        Pedido primero = servicio.registrar(pedidoMesa(5), meseroId);

        MesaOcupadaException e = assertThrows(MesaOcupadaException.class,
                () -> servicio.registrar(pedidoMesa(5), meseroId));
        assertEquals(5, e.getNumeroMesa());
        assertEquals(primero.getId(), e.getPedidoId());
        assertEquals("La mesa 5 está ocupada (la atiende Laura Gómez). Se libera cuando su pedido se cobre o se "
                + "cancele, o cuando se cambie a otra mesa.", e.getMessage());     // sin el número del pedido
        assertTrue(e instanceof OperacionNoPermitidaException);              // el API responde 409

        assertEquals(List.of(new MesaOcupada(5, primero.getId(), "Laura Gómez", EstadoCocina.PENDIENTE)),
                servicio.mesasOcupadas());
    }

    @Test
    @DisplayName("La mesa se libera al cobrar el pedido o al cancelarlo")
    void mesaSeLibera() {
        Pedido cancelado = servicio.registrar(pedidoMesa(5), meseroId);
        servicio.cancelar(cancelado.getId());
        Pedido cobrado = servicio.registrar(pedidoMesa(5), meseroId);         // libre otra vez

        cocina.cambiarEstado(cobrado.getId(), EstadoCocina.EN_PREPARACION);
        cocina.cambiarEstado(cobrado.getId(), EstadoCocina.LISTO);
        cocina.cambiarEstado(cobrado.getId(), EstadoCocina.ENTREGADO);
        assertThrows(MesaOcupadaException.class, () -> servicio.registrar(pedidoMesa(5), meseroId));  // aún sin cobrar
        servicio.registrarVenta(cobrado.getId(), MetodoPago.EFECTIVO);

        assertEquals(5, servicio.registrar(pedidoMesa(5), meseroId).getNumeroMesa());
    }

    @Test
    @DisplayName("Cambiar de mesa: el pedido pasa a una mesa libre y la anterior queda libre")
    void cambiarDeMesa() {
        Pedido pedido = servicio.registrar(pedidoMesa(5), meseroId);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.EN_PREPARACION);   // en cualquier estado de cocina

        Pedido movido = servicio.cambiarMesa(pedido.getId(), 8);
        assertEquals(8, movido.getNumeroMesa());
        assertEquals(List.of(8), servicio.mesasOcupadas().stream().map(MesaOcupada::numeroMesa).toList());
        servicio.registrar(pedidoMesa(5), meseroId);                         // la 5 quedó libre
    }

    @Test
    @DisplayName("No se cambia a una mesa ocupada, a la misma mesa ni un pedido ya cobrado o cancelado")
    void cambiarDeMesaReglas() {
        Pedido enLa5 = servicio.registrar(pedidoMesa(5), meseroId);
        Pedido enLa8 = servicio.registrar(pedidoMesa(8), meseroId);

        assertThrows(MesaOcupadaException.class, () -> servicio.cambiarMesa(enLa5.getId(), 8));
        assertThrows(DomainException.class, () -> servicio.cambiarMesa(enLa5.getId(), 5));
        assertThrows(DomainException.class, () -> servicio.cambiarMesa(enLa5.getId(), 0));
        servicio.cancelar(enLa8.getId());
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.cambiarMesa(enLa8.getId(), 9));
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

        Pedido pagado = servicio.registrarVenta(pedido.getId(), MetodoPago.TRANSFERENCIA);
        assertEquals(EstadoPedido.PAGADO, pagado.getEstadoPedido());
        assertEquals(MetodoPago.TRANSFERENCIA, pagado.getMetodoPago());
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

    @Test
    @DisplayName("RN · Los pedidos se numeran por día: #1, #2... y al día siguiente vuelve a empezar en #1")
    void numeroDelDia() {
        assertEquals(1, servicio.registrar(pedidoMesa(1), meseroId).getNumeroDelDia());
        assertEquals(2, servicio.registrar(pedidoMesa(2), meseroId).getNumeroDelDia());
        Pedido cancelado = servicio.registrar(pedidoMesa(3), meseroId);
        servicio.cancelar(cancelado.getId());
        assertEquals(4, servicio.registrar(pedidoMesa(3), meseroId).getNumeroDelDia());   // los cancelados también cuentan

        reloj.avanzarMinutos(24 * 60);                                                   // día siguiente
        Pedido primeroDeManana = servicio.registrar(pedidoMesa(9), meseroId);
        assertEquals(1, primeroDeManana.getNumeroDelDia());
        assertTrue(primeroDeManana.getId() > 4);                                         // el id interno sigue creciendo
    }

    @Test
    @DisplayName("RN · Adición: el cliente pide algo más con el pedido ya entregado; vuelve a cocina y suma a la cuenta")
    void adicion() {
        Pedido pedido = servicio.registrar(pedidoMesa(5), meseroId);
        List<LineaPedido> otraLimonada = List.of(new LineaPedido(limonadaId, 1, "Sin azúcar"));
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.agregarAdicion(pedido.getId(), otraLimonada));

        cocina.cambiarEstado(pedido.getId(), EstadoCocina.EN_PREPARACION);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.LISTO);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.ENTREGADO);
        assertEquals(0, cocina.comandasPendientes().size());

        Pedido conAdicion = servicio.agregarAdicion(pedido.getId(), otraLimonada);
        assertEquals(3, conAdicion.getDetalles().size());
        assertTrue(conAdicion.getDetalles().get(2).isAdicion());
        assertEquals(0, new BigDecimal("82000").compareTo(conAdicion.getTotal()));   // 73.000 + 9.000
        assertEquals(EstadoCocina.EN_PREPARACION, conAdicion.getEstadoCocina());
        assertEquals(1, cocina.comandasPendientes().size());                         // la cocina la ve otra vez

        assertThrows(DomainException.class, () -> servicio.agregarAdicion(pedido.getId(), List.of()));
        List<LineaPedido> inexistente = List.of(new LineaPedido(999L, 1, null));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.agregarAdicion(pedido.getId(), inexistente));
    }
}
