package com.udec.restoflow.domain.model;

/**
 * Estado comercial del pedido (separado del estado de cocina).
 * ABIERTO: en atención · PAGADO: venta registrada · CANCELADO: el cliente ya no lo quiso.
 */
public enum EstadoPedido {
    ABIERTO,
    PAGADO,
    CANCELADO
}
