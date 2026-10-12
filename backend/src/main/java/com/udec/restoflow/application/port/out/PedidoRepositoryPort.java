package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: guardar y consultar pedidos con sus líneas. */
public interface PedidoRepositoryPort {

    Pedido guardar(Pedido pedido);

    Optional<Pedido> buscarPorId(Long id);

    /** Filtros opcionales (null = sin filtro). Ordenados del más reciente al más antiguo. */
    List<Pedido> buscar(Integer numeroMesa, EstadoCocina estadoCocina, EstadoPedido estadoPedido);

    /** RF-04 · Pedidos abiertos aún no entregados, del más antiguo al más reciente. */
    List<Pedido> pendientesEnCocina();
}
