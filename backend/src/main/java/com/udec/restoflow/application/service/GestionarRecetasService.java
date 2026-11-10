package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarRecetasUseCase;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.application.port.out.RecetaRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** RF-06 · El administrador define la receta de cada producto; de ella sale el descuento automático. */
@Transactional
public class GestionarRecetasService implements GestionarRecetasUseCase {

    private final RecetaRepositoryPort recetas;
    private final ProductoRepositoryPort productos;
    private final IngredienteRepositoryPort ingredientes;

    public GestionarRecetasService(RecetaRepositoryPort recetas, ProductoRepositoryPort productos,
                                   IngredienteRepositoryPort ingredientes) {
        this.recetas = recetas;
        this.productos = productos;
        this.ingredientes = ingredientes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecetaIngrediente> receta(Long productoId) {
        validarProducto(productoId);
        return recetas.deProducto(productoId);
    }

    @Override
    public List<RecetaIngrediente> definir(Long productoId, List<LineaReceta> lineas) {
        validarProducto(productoId);
        List<LineaReceta> nuevas = lineas == null ? List.of() : lineas;
        Set<Long> vistos = new HashSet<>();
        for (LineaReceta linea : nuevas) {
            if (linea.ingredienteId() == null) {
                throw new DomainException("Cada línea de la receta debe indicar el ingrediente");
            }
            if (!vistos.add(linea.ingredienteId())) {
                throw new DomainException("Un ingrediente aparece dos veces en la receta");
            }
            ingredientes.buscarPorId(linea.ingredienteId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Ingrediente", linea.ingredienteId()));
        }
        List<RecetaIngrediente> receta = nuevas.stream()
                .map(l -> new RecetaIngrediente(null, productoId, l.ingredienteId(), l.cantidad()))
                .toList();
        return recetas.reemplazar(productoId, receta);
    }

    private void validarProducto(Long productoId) {
        productos.buscarPorId(productoId).orElseThrow(() -> new RecursoNoEncontradoException("Producto", productoId));
    }
}
