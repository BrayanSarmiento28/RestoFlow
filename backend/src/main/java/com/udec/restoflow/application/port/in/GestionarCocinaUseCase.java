package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.Pedido;

import java.util.List;

/** RF-03 y RF-04 · Lo que hace el personal de cocina. */
public interface GestionarCocinaUseCase {

    /** Comandas por atender, del pedido más antiguo al más reciente. */
    List<Pedido> comandasPendientes();

    /** Avanza el estado de preparación (Pendiente → En preparación → Listo → Entregado). */
    Pedido cambiarEstado(Long pedidoId, EstadoCocina nuevoEstado);
}
