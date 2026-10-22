package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarRecetasUseCase.LineaReceta;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-06 · Recetas: cuánto de cada insumo lleva un producto. */
class GestionarRecetasServiceTest {

    private DoblesInventario.RecetasEnMemoria recetas;
    private GestionarRecetasService servicio;
    private Long bandeja;
    private Long arroz;
    private Long huevo;

    @BeforeEach
    void preparar() {
        recetas = new DoblesInventario.RecetasEnMemoria();
        DoblesPedidos.ProductosEnMemoria productos = new DoblesPedidos.ProductosEnMemoria();
        DoblesInventario.IngredientesEnMemoria ingredientes = new DoblesInventario.IngredientesEnMemoria();
        servicio = new GestionarRecetasService(recetas, productos, ingredientes);
        bandeja = productos.guardar(new Producto(null, "Bandeja paisa", null, new BigDecimal("32000"), 1L, "Platos", true)).getId();
        arroz = ingredientes.guardar(Ingrediente.nuevo("Arroz", UnidadMedida.GRAMO, new BigDecimal("5000"), new BigDecimal("1000"))).getId();
        huevo = ingredientes.guardar(Ingrediente.nuevo("Huevo", UnidadMedida.UNIDAD, new BigDecimal("30"), new BigDecimal("6"))).getId();
    }

    @Test
    @DisplayName("Define la receta y la consulta")
    void definir() {
        servicio.definir(bandeja, List.of(new LineaReceta(arroz, new BigDecimal("150")), new LineaReceta(huevo, BigDecimal.ONE)));

        assertThat(servicio.receta(bandeja)).extracting(RecetaIngrediente::getIngredienteId).containsExactly(arroz, huevo);
    }

    @Test
    @DisplayName("Definirla otra vez reemplaza la anterior (no se acumulan líneas)")
    void reemplazar() {
        servicio.definir(bandeja, List.of(new LineaReceta(arroz, new BigDecimal("150"))));
        servicio.definir(bandeja, List.of(new LineaReceta(huevo, new BigDecimal("2"))));

        assertThat(servicio.receta(bandeja)).singleElement()
                .satisfies(l -> assertThat(l.getCantidad()).isEqualByComparingTo("2"));
    }

    @Test
    @DisplayName("Una lista vacía (o null) deja el producto sin receta")
    void vaciar() {
        servicio.definir(bandeja, List.of(new LineaReceta(arroz, new BigDecimal("150"))));
        servicio.definir(bandeja, null);
        assertThat(servicio.receta(bandeja)).isEmpty();
    }

    @Test
    @DisplayName("No acepta el mismo ingrediente dos veces ni líneas sin ingrediente")
    void lineasInvalidas() {
        assertThatThrownBy(() -> servicio.definir(bandeja, List.of(new LineaReceta(arroz, BigDecimal.ONE),
                new LineaReceta(arroz, BigDecimal.TEN)))).isInstanceOf(DomainException.class).hasMessageContaining("dos veces");
        assertThatThrownBy(() -> servicio.definir(bandeja, List.of(new LineaReceta(null, BigDecimal.ONE))))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> servicio.definir(bandeja, List.of(new LineaReceta(arroz, BigDecimal.ZERO))))
                .isInstanceOf(DomainException.class);
        assertThat(recetas.datos).isEmpty();
    }

    @Test
    @DisplayName("El producto y los ingredientes deben existir")
    void noExisten() {
        assertThatThrownBy(() -> servicio.receta(99L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.definir(bandeja, List.of(new LineaReceta(99L, BigDecimal.ONE))))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("Ingrediente");
    }
}
