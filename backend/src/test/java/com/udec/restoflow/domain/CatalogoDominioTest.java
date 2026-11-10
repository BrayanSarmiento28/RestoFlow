package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.domain.model.Producto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-04 · Reglas del menú: categorías y productos. */
class CatalogoDominioTest {

    private final Categoria bebidas = new Categoria(2L, "Bebidas", true);

    @Test
    @DisplayName("Una categoría nueva queda activa y sin espacios sobrantes")
    void categoriaNueva() {
        Categoria c = Categoria.nueva("  Sopas  ");
        assertThat(c.getNombre()).isEqualTo("Sopas");
        assertThat(c.isActiva()).isTrue();
        c.desactivar();
        assertThat(c.isActiva()).isFalse();
        c.renombrar("Sopas del día");
        assertThat(c.getNombre()).isEqualTo("Sopas del día");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "  a "})
    @DisplayName("El nombre de la categoría necesita al menos 3 letras")
    void categoriaNombreCorto(String nombre) {
        assertThatThrownBy(() -> Categoria.nueva(nombre)).isInstanceOf(DomainException.class)
                .hasMessageContaining("al menos 3 caracteres");
    }

    @Test
    @DisplayName("Un producto nuevo toma la categoría, queda activo y se puede vender")
    void productoNuevo() {
        Producto p = Producto.nuevo(" Limonada de coco ", " Natural ", new BigDecimal("9000"), bebidas);
        assertThat(p.getNombre()).isEqualTo("Limonada de coco");
        assertThat(p.getDescripcion()).isEqualTo("Natural");
        assertThat(p.getCategoriaId()).isEqualTo(2L);
        assertThat(p.getCategoriaNombre()).isEqualTo("Bebidas");
        assertThat(p.isActivo()).isTrue();
        assertThatCode(p::validarDisponibleParaVenta).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un producto desactivado no se puede vender (nunca se borra)")
    void productoDesactivado() {
        Producto p = Producto.nuevo("Ajiaco", null, new BigDecimal("28000"), bebidas);
        p.desactivar();
        assertThatThrownBy(p::validarDisponibleParaVenta).isInstanceOf(OperacionNoPermitidaException.class)
                .hasMessageContaining("Ajiaco");
        p.activar();
        assertThatCode(p::validarDisponibleParaVenta).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1000"})
    @DisplayName("El precio debe ser mayor que cero")
    void precioInvalido(String precio) {
        assertThatThrownBy(() -> Producto.nuevo("Ajiaco", null, new BigDecimal(precio), bebidas))
                .isInstanceOf(DomainException.class).hasMessageContaining("precio");
        assertThatThrownBy(() -> Producto.nuevo("Ajiaco", null, null, bebidas)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("El producto debe tener nombre de 3 letras y una categoría")
    void nombreYCategoria() {
        assertThatThrownBy(() -> Producto.nuevo("Té", null, BigDecimal.TEN, bebidas)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Producto(null, "Ajiaco", null, BigDecimal.TEN, null, null, true))
                .isInstanceOf(DomainException.class).hasMessageContaining("categoría");
    }

    @Test
    @DisplayName("Actualizar cambia nombre, precio y categoría")
    void actualizar() {
        Producto p = Producto.nuevo("Jugo de mora", null, new BigDecimal("7000"), bebidas);
        p.actualizar("Jugo de mora en leche", "Grande", new BigDecimal("8500"), new Categoria(3L, "Jugos", true));
        assertThat(p.getNombre()).isEqualTo("Jugo de mora en leche");
        assertThat(p.getPrecio()).isEqualByComparingTo("8500");
        assertThat(p.getCategoriaNombre()).isEqualTo("Jugos");
    }
}
