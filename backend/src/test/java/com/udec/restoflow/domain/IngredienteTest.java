package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF-05 a RF-07 · Reglas de las entidades de inventario. */
class IngredienteTest {

    private Ingrediente arroz() {
        return new Ingrediente(1L, "Arroz", UnidadMedida.GRAMO, new BigDecimal("1000"), new BigDecimal("300"), true);
    }

    @Test
    @DisplayName("El stock nunca queda negativo")
    void noQuedaNegativo() {
        Ingrediente arroz = arroz();
        assertThrows(OperacionNoPermitidaException.class, () -> arroz.descontar(new BigDecimal("1500")));
        assertEquals(0, new BigDecimal("1000").compareTo(arroz.getStockActual()));
    }

    @Test
    @DisplayName("Avisa cuando el stock llega al mínimo")
    void detectaStockMinimo() {
        Ingrediente arroz = arroz();
        assertFalse(arroz.estaEnMinimo());
        arroz.descontar(new BigDecimal("700"));
        assertTrue(arroz.estaEnMinimo());
    }

    @Test
    @DisplayName("La receta calcula el consumo según las unidades vendidas")
    void recetaCalculaConsumo() {
        RecetaIngrediente receta = new RecetaIngrediente(1L, 10L, 1L, new BigDecimal("200"));
        assertEquals(0, new BigDecimal("600").compareTo(receta.consumoPara(3)));
    }

    @Test
    @DisplayName("Las cantidades deben ser positivas")
    void cantidadesPositivas() {
        assertThrows(DomainException.class, () -> arroz().ingresar(BigDecimal.ZERO));
        assertThrows(DomainException.class, () -> new RecetaIngrediente(1L, 10L, 1L, new BigDecimal("-1")));
    }

    @Test
    @DisplayName("RF-06 · En una venta, si no alcanza, el stock queda en 0 y se informa cuánto faltó")
    void consumoPorVentaNoBloquea() {
        Ingrediente arroz = arroz();
        assertEquals(0, BigDecimal.ZERO.compareTo(arroz.consumir(new BigDecimal("400"))));
        assertEquals(0, new BigDecimal("600").compareTo(arroz.getStockActual()));

        BigDecimal faltante = arroz.consumir(new BigDecimal("750"));
        assertEquals(0, new BigDecimal("150").compareTo(faltante));
        assertTrue(arroz.estaAgotado());
    }

    @Test
    @DisplayName("Editar un insumo no cambia su stock y valida los datos")
    void actualizarNoTocaStock() {
        Ingrediente arroz = arroz();
        arroz.actualizar("Arroz blanco", UnidadMedida.KILOGRAMO, new BigDecimal("2"));
        assertEquals("Arroz blanco", arroz.getNombre());
        assertEquals(0, new BigDecimal("1000").compareTo(arroz.getStockActual()));
        assertThrows(DomainException.class, () -> arroz.actualizar("A", UnidadMedida.GRAMO, BigDecimal.ONE));
        assertThrows(DomainException.class, () -> arroz.actualizar("Arroz", UnidadMedida.GRAMO, new BigDecimal("-1")));
    }
}
