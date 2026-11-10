package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.MetodoPago;
import com.udec.restoflow.domain.model.Pedido;

import java.util.List;

/** RF-02 · Gestión de pedidos por parte del mesero o cajero. */
public interface GestionarPedidosUseCase {

    /** Registra el pedido y lo envía a cocina en estado Pendiente. */
    Pedido registrar(ComandoPedido comando, Long meseroId);

    /** Reemplaza las líneas y observaciones. Solo mientras esté Pendiente. */
    Pedido modificar(Long pedidoId, ComandoPedido comando);

    Pedido cancelar(Long pedidoId);

    /** Cierra un pedido Entregado como venta (Pagado), con su método de pago. */
    Pedido registrarVenta(Long pedidoId, MetodoPago metodo);

    Pedido obtener(Long pedidoId);

    List<Pedido> consultar(Integer numeroMesa, EstadoCocina estadoCocina, EstadoPedido estadoPedido);

    /**
     * El cliente pide algo más con el pedido ya en cocina: los productos se suman al mismo pedido como "adición"
     * (y a su cuenta). Si ya estaba Listo o Entregado, vuelve a En preparación.
     */
    Pedido agregarAdicion(Long pedidoId, List<LineaPedido> lineas);

    /** Pasa un pedido abierto a otra mesa, que debe estar libre (por ejemplo, el cliente se cambió de lugar). */
    Pedido cambiarMesa(Long pedidoId, int nuevaMesa);

    /** Mesas con un pedido abierto, de la menor a la mayor (para que el mesero sepa cuáles están libres). */
    List<MesaOcupada> mesasOcupadas();

    record MesaOcupada(int numeroMesa, Long pedidoId, String meseroNombre, EstadoCocina estadoCocina) { }

    record LineaPedido(Long productoId, int cantidad, String nota) { }

    record ComandoPedido(int numeroMesa, List<LineaPedido> lineas, String observaciones) { }
}
