package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

/** Agrupa los productos del menú (Entradas, Platos fuertes, Bebidas...). */
public class Categoria {

    private final Long id;
    private String nombre;
    private boolean activa;

    public Categoria(Long id, String nombre, boolean activa) {
        this.id = id;
        this.nombre = validarNombre(nombre);
        this.activa = activa;
    }

    public static Categoria nueva(String nombre) {
        return new Categoria(null, nombre, true);
    }

    public void renombrar(String nombre) {
        this.nombre = validarNombre(nombre);
    }

    public void activar() {
        this.activa = true;
    }

    public void desactivar() {
        this.activa = false;
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.trim().length() < 3) {
            throw new DomainException("El nombre de la categoría debe tener al menos 3 caracteres");
        }
        return nombre.trim();
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public boolean isActiva() { return activa; }
}
