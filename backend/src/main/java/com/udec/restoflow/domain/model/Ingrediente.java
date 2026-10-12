package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;

import java.math.BigDecimal;

/**
 * RF-05 · Insumo del inventario (arroz, pollo, aceite...). Se descuenta cuando se venden los productos
 * que lo usan en su receta (RF-06) y genera alertas al llegar a su stock mínimo (RF-07).
 * Sus casos de uso se implementan en el sprint de inventario; aquí quedan la entidad y sus reglas.
 */
public class Ingrediente {

    private final Long id;
    private String nombre;
    private UnidadMedida unidad;
    private BigDecimal stockActual;
    private BigDecimal stockMinimo;
    private boolean activo;

    public Ingrediente(Long id, String nombre, UnidadMedida unidad, BigDecimal stockActual, BigDecimal stockMinimo,
                       boolean activo) {
        if (nombre == null || nombre.trim().length() < 2) {
            throw new DomainException("El nombre del ingrediente debe tener al menos 2 caracteres");
        }
        if (unidad == null) {
            throw new DomainException("La unidad de medida es obligatoria");
        }
        this.id = id;
        this.nombre = nombre.trim();
        this.unidad = unidad;
        this.stockActual = noNegativo(stockActual, "El stock actual");
        this.stockMinimo = noNegativo(stockMinimo, "El stock mínimo");
        this.activo = activo;
    }

    /** Suma una compra o ingreso de mercancía. */
    public void ingresar(BigDecimal cantidad) {
        stockActual = stockActual.add(positiva(cantidad));
    }

    /** RF-06 · Resta lo consumido. Nunca deja el stock en negativo. */
    public void descontar(BigDecimal cantidad) {
        BigDecimal resultado = stockActual.subtract(positiva(cantidad));
        if (resultado.signum() < 0) {
            throw new OperacionNoPermitidaException("No hay suficiente " + nombre + ": quedan " + stockActual + " "
                    + unidad.name().toLowerCase());
        }
        stockActual = resultado;
    }

    /** Ajuste después de un conteo físico. */
    public void ajustarA(BigDecimal stockContado) {
        stockActual = noNegativo(stockContado, "El stock contado");
    }

    /** RF-07 · El insumo necesita reabastecerse. */
    public boolean estaEnMinimo() {
        return stockActual.compareTo(stockMinimo) <= 0;
    }

    public void activar() {
        activo = true;
    }

    public void desactivar() {
        activo = false;
    }

    private static BigDecimal noNegativo(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0) {
            throw new DomainException(campo + " no puede ser negativo");
        }
        return valor;
    }

    private static BigDecimal positiva(BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException("La cantidad debe ser mayor que cero");
        }
        return cantidad;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public UnidadMedida getUnidad() { return unidad; }
    public BigDecimal getStockActual() { return stockActual; }
    public BigDecimal getStockMinimo() { return stockMinimo; }
    public boolean isActivo() { return activo; }
}
