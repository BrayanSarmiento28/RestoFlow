package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.out.CatalogoExternoPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.ProductoExternoNoEncontradoException;
import com.udec.restoflow.domain.exception.ServicioExternoNoDisponibleException;
import com.udec.restoflow.domain.model.ProductoExterno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Caso de uso "buscar insumo por código de barras", con un doble de prueba del catálogo (sin internet). */
class BuscarProductoExternoServiceTest {

    /** Doble de CatalogoExternoPort: responde con los productos que le pongamos y anota qué se le pidió. */
    static class CatalogoFalso implements CatalogoExternoPort {
        final Map<String, ProductoExterno> productos = new HashMap<>();
        final List<String> consultados = new ArrayList<>();
        boolean caido;

        @Override
        public Optional<ProductoExterno> buscarPorCodigo(String codigo) {
            consultados.add(codigo);
            if (caido) {
                throw new ServicioExternoNoDisponibleException("Open Food Facts");
            }
            return Optional.ofNullable(productos.get(codigo));
        }
    }

    private final CatalogoFalso catalogo = new CatalogoFalso();
    private final BuscarProductoExternoService servicio = new BuscarProductoExternoService(catalogo);

    @Test
    @DisplayName("Encuentra el producto y consulta el catálogo con el código ya limpio")
    void encontrado() {
        ProductoExterno arroz = new ProductoExterno("7702011012345", "Arroz", "Diana", "1 kg", null, "Open Food Facts");
        catalogo.productos.put("7702011012345", arroz);

        assertThat(servicio.buscarPorCodigo(" 7702011-012345 ")).isEqualTo(arroz);
        assertThat(catalogo.consultados).containsExactly("7702011012345");
    }

    @Test
    @DisplayName("Si el catálogo no lo tiene responde 'no encontrado' con un mensaje útil")
    void noEncontrado() {
        assertThatThrownBy(() -> servicio.buscarPorCodigo("7702011012345"))
                .isInstanceOf(ProductoExternoNoEncontradoException.class)
                .hasMessageContaining("7702011012345")
                .hasMessageContaining("escribiendo los datos");
    }

    @Test
    @DisplayName("Un código inválido se rechaza sin llamar a internet")
    void codigoInvalido() {
        assertThatThrownBy(() -> servicio.buscarPorCodigo("12AB")).isInstanceOf(DomainException.class);
        assertThat(catalogo.consultados).isEmpty();
    }

    @Test
    @DisplayName("Si el servicio externo está caído, el error llega tal cual (el API responde 503)")
    void servicioCaido() {
        catalogo.caido = true;
        assertThatThrownBy(() -> servicio.buscarPorCodigo("7702011012345"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasMessageContaining("no está disponible");
    }
}
