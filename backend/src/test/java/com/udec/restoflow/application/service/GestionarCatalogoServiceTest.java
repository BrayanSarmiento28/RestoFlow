package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCatalogoUseCase.ComandoProducto;
import com.udec.restoflow.application.port.out.CategoriaRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.domain.model.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-04 · Menú: categorías y productos, con dobles en memoria (sin base de datos). */
class GestionarCatalogoServiceTest {

    /** Doble de CategoriaRepositoryPort. */
    static class CategoriasEnMemoria implements CategoriaRepositoryPort {
        final Map<Long, Categoria> datos = new LinkedHashMap<>();
        private long secuencia = 1;

        public Categoria guardar(Categoria c) {
            Long id = c.getId() != null ? c.getId() : secuencia++;
            Categoria copia = new Categoria(id, c.getNombre(), c.isActiva());
            datos.put(id, copia);
            return copia;
        }

        public Optional<Categoria> buscarPorId(Long id) {
            return Optional.ofNullable(datos.get(id));
        }

        public boolean existePorNombre(String nombre) {
            return datos.values().stream().anyMatch(c -> c.getNombre().equalsIgnoreCase(nombre));
        }

        public List<Categoria> listarTodas() {
            return List.copyOf(datos.values());
        }
    }

    private CategoriasEnMemoria categorias;
    private DoblesPedidos.ProductosEnMemoria productos;
    private GestionarCatalogoService servicio;
    private Long bebidas;

    @BeforeEach
    void preparar() {
        categorias = new CategoriasEnMemoria();
        productos = new DoblesPedidos.ProductosEnMemoria();
        servicio = new GestionarCatalogoService(categorias, productos);
        bebidas = servicio.crearCategoria("Bebidas").getId();
    }

    @Test
    @DisplayName("No se pueden crear dos categorías con el mismo nombre (sin importar mayúsculas)")
    void categoriaRepetida() {
        assertThatThrownBy(() -> servicio.crearCategoria("  BEBIDAS ")).isInstanceOf(OperacionNoPermitidaException.class)
                .hasMessageContaining("Ya existe la categoría");
        assertThat(servicio.listarCategorias()).hasSize(1);
    }

    @Test
    @DisplayName("Crea un producto dentro de su categoría y lo lista")
    void crearProducto() {
        Producto p = servicio.crearProducto(new ComandoProducto("Limonada de coco", "Natural", new BigDecimal("9000"), bebidas));

        assertThat(p.getId()).isNotNull();
        assertThat(p.getCategoriaNombre()).isEqualTo("Bebidas");
        assertThat(servicio.listarProductos(true, bebidas)).extracting(Producto::getNombre).containsExactly("Limonada de coco");
    }

    @Test
    @DisplayName("Sin categoría o con una que no existe, no se crea el producto")
    void categoriaFaltante() {
        assertThatThrownBy(() -> servicio.crearProducto(new ComandoProducto("Ajiaco", null, BigDecimal.TEN, null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("categoría es obligatoria");
        assertThatThrownBy(() -> servicio.crearProducto(new ComandoProducto("Ajiaco", null, BigDecimal.TEN, 99L)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Actualiza precio y categoría de un producto existente")
    void actualizar() {
        Long jugos = servicio.crearCategoria("Jugos").getId();
        Long id = servicio.crearProducto(new ComandoProducto("Jugo de mora", null, new BigDecimal("7000"), bebidas)).getId();

        Producto p = servicio.actualizarProducto(id, new ComandoProducto("Jugo de mora", "En leche", new BigDecimal("8500"), jugos));

        assertThat(p.getPrecio()).isEqualByComparingTo("8500");
        assertThat(servicio.obtenerProducto(id).getCategoriaNombre()).isEqualTo("Jugos");
    }

    @Test
    @DisplayName("Desactivar un producto lo saca de la lista de venta, pero no lo borra")
    void disponibilidad() {
        Long id = servicio.crearProducto(new ComandoProducto("Avena", null, new BigDecimal("5000"), bebidas)).getId();

        servicio.cambiarDisponibilidad(id, false);
        assertThat(servicio.listarProductos(true, null)).isEmpty();
        assertThat(servicio.listarProductos(false, null)).hasSize(1);

        servicio.cambiarDisponibilidad(id, true);
        assertThat(servicio.listarProductos(true, null)).hasSize(1);
    }

    @Test
    @DisplayName("Un producto que no existe responde 'no encontrado'")
    void noExiste() {
        assertThatThrownBy(() -> servicio.obtenerProducto(404L)).isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("404");
    }
}
