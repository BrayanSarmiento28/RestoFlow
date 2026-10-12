package com.udec.restoflow.domain.model;

/** RF-05 / RF-12 · Tipos de movimiento de inventario. */
public enum TipoMovimiento {
    /** Compra o ingreso de mercancía: suma al stock. */
    ENTRADA,
    /** Consumo por una venta o salida manual: resta del stock. */
    SALIDA,
    /** Corrección después de un conteo físico: deja el stock en el valor contado. */
    AJUSTE
}
