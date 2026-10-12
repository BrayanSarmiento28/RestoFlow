package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.domain.model.Producto;

import java.math.BigDecimal;
import java.util.List;

/** Menú del restaurante: categorías y productos que se pueden vender. */
public interface GestionarCatalogoUseCase {

    List<Categoria> listarCategorias();

    Categoria crearCategoria(String nombre);

    List<Producto> listarProductos(boolean soloActivos, Long categoriaId);

    Producto obtenerProducto(Long id);

    Producto crearProducto(ComandoProducto comando);

    Producto actualizarProducto(Long id, ComandoProducto comando);

    /** Activa o desactiva un producto para la venta (nunca se borra). */
    Producto cambiarDisponibilidad(Long id, boolean activo);

    record ComandoProducto(String nombre, String descripcion, BigDecimal precio, Long categoriaId) { }
}
