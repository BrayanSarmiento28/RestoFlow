package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;

import java.math.BigDecimal;

/**
 * RF-05 · Insumo del inventario (arroz, pollo, aceite...). Se descuenta cuando se venden los productos
 * que lo usan en su receta (RF-06) y genera alertas al llegar a su stock mínimo (RF-07).
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
        this.id = id;
        this.nombre = nombreValido(nombre);
        this.unidad = unidadValida(unidad);
        this.stockActual = noNegativo(stockActual, "El stock actual");
        this.stockMinimo = noNegativo(stockMinimo, "El stock mínimo");
        this.activo = activo;
    }

    /** Insumo recién creado: queda activo con el stock inicial indicado. */
    public static Ingrediente nuevo(String nombre, UnidadMedida unidad, BigDecimal stockInicial, BigDecimal stockMinimo) {
        return new Ingrediente(null, nombre, unidad, stockInicial == null ? BigDecimal.ZERO : stockInicial,
                stockMinimo, true);
    }

    /** Cambia los datos descriptivos. El stock solo cambia con movimientos (entrada, salida o ajuste). */
    public void actualizar(String nombre, UnidadMedida unidad, BigDecimal stockMinimo) {
        this.nombre = nombreValido(nombre);
        this.unidad = unidadValida(unidad);
        this.stockMinimo = noNegativo(stockMinimo, "El stock mínimo");
    }

    /** Suma una compra o ingreso de mercancía. */
    public void ingresar(BigDecimal cantidad) {
        stockActual = stockActual.add(positiva(cantidad));
    }

    /** Salida manual (merma, daño, vencimiento). No se puede sacar más de lo que hay. */
    public void descontar(BigDecimal cantidad) {
        BigDecimal resultado = stockActual.subtract(positiva(cantidad));
        if (resultado.signum() < 0) {
            throw new OperacionNoPermitidaException("No hay suficiente " + nombre + ": quedan "
                    + stockActual.stripTrailingZeros().toPlainString() + " " + unidad.name().toLowerCase());
        }
        stockActual = resultado;
    }

    /**
     * RF-06 · Consumo por una venta. El plato ya se sirvió, así que la venta nunca se bloquea:
     * si no alcanza, el stock queda en cero y se devuelve cuánto faltó (para generar una alerta).
     */
    public BigDecimal consumir(BigDecimal cantidad) {
        BigDecimal resultado = stockActual.subtract(positiva(cantidad));
        if (resultado.signum() < 0) {
            stockActual = BigDecimal.ZERO;
            return resultado.negate();
        }
        stockActual = resultado;
        return BigDecimal.ZERO;
    }

    /** Ajuste después de un conteo físico. */
    public void ajustarA(BigDecimal stockContado) {
        stockActual = noNegativo(stockContado, "El stock contado");
    }

    /** RF-07 · El insumo necesita reabastecerse. */
    public boolean estaEnMinimo() {
        return stockActual.compareTo(stockMinimo) <= 0;
    }

    public boolean estaAgotado() {
        return stockActual.signum() == 0;
    }

    public void activar() {
        activo = true;
    }

    public void desactivar() {
        activo = false;
    }

    private static String nombreValido(String nombre) {
        if (nombre == null || nombre.trim().length() < 2) {
            throw new DomainException("El nombre del ingrediente debe tener al menos 2 caracteres");
        }
        return nombre.trim();
    }

    private static UnidadMedida unidadValida(UnidadMedida unidad) {
        if (unidad == null) {
            throw new DomainException("La unidad de medida es obligatoria");
        }
        return unidad;
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
