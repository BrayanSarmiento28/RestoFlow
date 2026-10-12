package com.udec.restoflow.domain.model;

/**
 * RF-03 · Estados de preparación de un pedido. Avanzan siempre en este orden y sin saltos:
 * PENDIENTE → EN_PREPARACION → LISTO → ENTREGADO.
 */
public enum EstadoCocina {

    PENDIENTE("Pendiente"),
    EN_PREPARACION("En preparación"),
    LISTO("Listo"),
    ENTREGADO("Entregado");

    private final String descripcion;

    EstadoCocina(String descripcion) {
        this.descripcion = descripcion;
    }

    /** El único estado al que se puede pasar desde este (null si ya es el último). */
    public EstadoCocina siguiente() {
        return this == ENTREGADO ? null : values()[ordinal() + 1];
    }

    public String getDescripcion() {
        return descripcion;
    }
}
