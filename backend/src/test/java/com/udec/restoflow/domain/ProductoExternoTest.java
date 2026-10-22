package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.ProductoExterno;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas del producto que llega del catálogo externo: código de barras, unidad y nombre sugeridos. */
class ProductoExternoTest {

    private static ProductoExterno producto(String nombre, String marca, String cantidad) {
        return new ProductoExterno("7702011012345", nombre, marca, cantidad, null, "Open Food Facts");
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "7702011012345, 7702011012345",
            "'7702 0110 12345', 7702011012345",
            "'7-702011-012345', 7702011012345",
            "12345678, 12345678",
            "012345678905, 012345678905",
            "17702011012342, 17702011012342"})
    @DisplayName("Acepta EAN-8, UPC-12, EAN-13 y GTIN-14, quitando espacios y guiones")
    void codigosValidos(String escrito, String esperado) {
        assertThat(ProductoExterno.validarCodigo(escrito)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "\"{0}\" no es válido")
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "123456789", "77020110123A5", "770201101234567", "abc"})
    @DisplayName("Rechaza códigos con letras o con un largo que no existe")
    void codigosInvalidos(String escrito) {
        assertThatThrownBy(() -> ProductoExterno.validarCodigo(escrito))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("8, 12, 13 o 14 números");
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "1 kg, KILOGRAMO", "'2,5kg', KILOGRAMO", "1 Kilo, KILOGRAMO",
            "500 g, GRAMO", "500g, GRAMO", "250 gr, GRAMO", "454 gramos, GRAMO",
            "1 L, LITRO", "1.5l, LITRO", "3 litros, LITRO", "1 lt, LITRO",
            "250 ml, MILILITRO", "350 cc, MILILITRO",
            "30 unidades, UNIDAD", "x 12, UNIDAD", "75 cl, UNIDAD"})
    @DisplayName("Sugiere la unidad del inventario según lo que dice el empaque")
    void unidadSugerida(String cantidad, UnidadMedida esperada) {
        assertThat(producto("Arroz", null, cantidad).unidadSugerida()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("Sin cantidad en el empaque la unidad sugerida es UNIDAD")
    void unidadSinDato() {
        assertThat(producto("Huevos", null, null).unidadSugerida()).isEqualTo(UnidadMedida.UNIDAD);
    }

    @Test
    @DisplayName("El nombre sugerido agrega la primera marca si no está ya en el nombre")
    void nombreConMarca() {
        assertThat(producto("Arroz blanco", "Diana, Grupo Diana", "1 kg").nombreSugerido()).isEqualTo("Arroz blanco · Diana");
        assertThat(producto("Arroz Diana", "Diana", "1 kg").nombreSugerido()).isEqualTo("Arroz Diana");
        assertThat(producto("  Leche entera  ", " ", "1 L").nombreSugerido()).isEqualTo("Leche entera");
    }

    @Test
    @DisplayName("Sin nombre en el catálogo se usa el código; y nunca pasa de 100 caracteres")
    void nombreVacioOLargo() {
        assertThat(producto(null, null, null).nombreSugerido()).isEqualTo("Producto 7702011012345");
        assertThat(producto("x".repeat(150), null, null).nombreSugerido()).hasSize(100);
    }
}
