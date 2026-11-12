package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarStockCocinaUseCase.StockBajo;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Usabilidad · La comanda de cocina muestra los insumos de cada plato que están en su mínimo o agotados. */
class ConsultarStockCocinaServiceTest {

    private static final long BANDEJA = 1L;
    private static final long JUGO = 2L;

    private DoblesInventario.IngredientesEnMemoria ingredientes;
    private DoblesInventario.RecetasEnMemoria recetas;
    private ConsultarStockCocinaService servicio;
    private long aguacate;
    private long arroz;
    private long mora;

    @BeforeEach
    void preparar() {
        ingredientes = new DoblesInventario.IngredientesEnMemoria();
        recetas = new DoblesInventario.RecetasEnMemoria();
        servicio = new ConsultarStockCocinaService(recetas, ingredientes);
        aguacate = insumo("Aguacate", UnidadMedida.UNIDAD, "2", "6", true);       // en su mínimo
        arroz = insumo("Arroz", UnidadMedida.GRAMO, "12000", "3000", true);     // alcanza
        mora = insumo("Mora", UnidadMedida.GRAMO, "0", "500", true);            // agotada
        recetas.reemplazar(BANDEJA, List.of(linea(BANDEJA, aguacate, "1"), linea(BANDEJA, arroz, "200")));
        recetas.reemplazar(JUGO, List.of(linea(JUGO, mora, "150")));
    }

    private long insumo(String nombre, UnidadMedida unidad, String stock, String minimo, boolean activo) {
        return ingredientes.guardar(new Ingrediente(null, nombre, unidad, new BigDecimal(stock), new BigDecimal(minimo),
                activo)).getId();
    }

    private static RecetaIngrediente linea(long productoId, long ingredienteId, String cantidad) {
        return new RecetaIngrediente(null, productoId, ingredienteId, new BigDecimal(cantidad));
    }

    @Test
    @DisplayName("Solo avisa los insumos en su mínimo o agotados de los platos pedidos, con su stock")
    void avisaLosQueSeAcaban() {
        List<StockBajo> avisos = servicio.stockBajo(List.of(BANDEJA, JUGO));

        assertThat(avisos).extracting(StockBajo::insumo).containsExactly("Aguacate", "Mora");   // el arroz alcanza
        StockBajo deAguacate = avisos.get(0);
        assertThat(deAguacate.productoId()).isEqualTo(BANDEJA);
        assertThat(deAguacate.stockActual()).isEqualByComparingTo("2");
        assertThat(deAguacate.unidad()).isEqualTo(UnidadMedida.UNIDAD);
        assertThat(deAguacate.agotado()).isFalse();
        assertThat(avisos.get(1).agotado()).isTrue();
    }

    @Test
    @DisplayName("Si todo alcanza, o no hay comandas, la lista está vacía")
    void sinAvisos() {
        assertThat(servicio.stockBajo(List.of())).isEmpty();
        assertThat(servicio.stockBajo(List.of(99L))).isEmpty();   // producto sin receta
    }

    @Test
    @DisplayName("Un insumo desactivado ya no se usa: no genera aviso")
    void insumoDesactivado() {
        long hielo = insumo("Hielo", UnidadMedida.KILOGRAMO, "0", "2", false);
        recetas.reemplazar(3L, List.of(linea(3L, hielo, "1")));

        assertThat(servicio.stockBajo(List.of(3L))).isEmpty();
    }
}
