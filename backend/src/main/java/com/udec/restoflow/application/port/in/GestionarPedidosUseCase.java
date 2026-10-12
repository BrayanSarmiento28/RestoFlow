package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;

import java.util.List;

/** RF-02 · Gestión de pedidos por parte del mesero o cajero. */
public interface GestionarPedidosUseCase {

    /** Registra el pedido y lo envía a cocina en estado Pendiente. */
    Pedido registrar(ComandoPedido comando, Long meseroId);

    /** Reemplaza las líneas y observaciones. Solo mientras esté Pendiente. */
    Pedido modificar(Long pedidoId, ComandoPedido comando);

    Pedido cancelar(Long pedidoId);

    /** Cierra un pedido Entregado como venta (Pagado). */
    Pedido registrarVenta(Long pedidoId);

    Pedido obtener(Long pedidoId);

    List<Pedido> consultar(Integer numeroMesa, EstadoCocina estadoCocina, EstadoPedido estadoPedido);

    record LineaPedido(Long productoId, int cantidad, String nota) { }

    record ComandoPedido(int numeroMesa, List<LineaPedido> lineas, String observaciones) { }
}
