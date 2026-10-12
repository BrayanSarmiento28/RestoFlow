package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCatalogoUseCase;
import com.udec.restoflow.application.port.out.CategoriaRepositoryPort;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.domain.model.Producto;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Administra el menú: categorías y productos. Crear y editar es solo para ADMIN (ver SecurityConfig). */
@Transactional
public class GestionarCatalogoService implements GestionarCatalogoUseCase {

    private final CategoriaRepositoryPort categorias;
    private final ProductoRepositoryPort productos;

    public GestionarCatalogoService(CategoriaRepositoryPort categorias, ProductoRepositoryPort productos) {
        this.categorias = categorias;
        this.productos = productos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Categoria> listarCategorias() {
        return categorias.listarTodas();
    }

    @Override
    public Categoria crearCategoria(String nombre) {
        Categoria nueva = Categoria.nueva(nombre);
        if (categorias.existePorNombre(nueva.getNombre())) {
            throw new OperacionNoPermitidaException("Ya existe la categoría " + nueva.getNombre());
        }
        return categorias.guardar(nueva);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> listarProductos(boolean soloActivos, Long categoriaId) {
        return productos.listar(soloActivos, categoriaId);
    }

    @Override
    @Transactional(readOnly = true)
    public Producto obtenerProducto(Long id) {
        return productos.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Producto", id));
    }

    @Override
    public Producto crearProducto(ComandoProducto comando) {
        Categoria categoria = categoria(comando.categoriaId());
        return productos.guardar(Producto.nuevo(comando.nombre(), comando.descripcion(), comando.precio(), categoria));
    }

    @Override
    public Producto actualizarProducto(Long id, ComandoProducto comando) {
        Producto producto = obtenerProducto(id);
        producto.actualizar(comando.nombre(), comando.descripcion(), comando.precio(), categoria(comando.categoriaId()));
        return productos.guardar(producto);
    }

    @Override
    public Producto cambiarDisponibilidad(Long id, boolean activo) {
        Producto producto = obtenerProducto(id);
        if (activo) {
            producto.activar();
        } else {
            producto.desactivar();
        }
        return productos.guardar(producto);
    }

    private Categoria categoria(Long id) {
        if (id == null) {
            throw new DomainException("La categoría es obligatoria");
        }
        return categorias.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Categoría", id));
    }
}
