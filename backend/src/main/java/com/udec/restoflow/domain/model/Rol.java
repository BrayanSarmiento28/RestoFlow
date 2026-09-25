package com.udec.restoflow.domain.model;

/**
 * Roles del personal del restaurante (control de acceso basado en roles, RF-01).
 * Son fijos: el sistema no permite crear roles nuevos desde la aplicación.
 */
public enum Rol {

    ADMIN("Administrador"),
    MESERO("Mesero / Cajero"),
    COCINA("Personal de cocina"),
    INVENTARIO("Personal de inventario");

    private final String descripcion;

    Rol(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
