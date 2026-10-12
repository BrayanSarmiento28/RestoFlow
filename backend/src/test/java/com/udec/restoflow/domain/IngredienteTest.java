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

/** Reglas de las entidades de inventario que se usarán en el sprint de inventario. */
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
}
