package com.udec.restoflow.domain.model;

/** Cómo pagó el cliente al registrar la venta. Por ahora efectivo y transferencia. */
public enum MetodoPago {
    EFECTIVO("Efectivo"),
    TRANSFERENCIA("Transferencia");

    private final String descripcion;

    MetodoPago(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
