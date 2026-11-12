package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarStockCocinaUseCase;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.RecetaRepositoryPort;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Usabilidad · Para cada producto de las comandas, los insumos de su receta que están en su mínimo o agotados.
 * Son dos consultas (las recetas de esos productos y sus insumos), sin importar cuántas comandas haya.
 */
@Transactional(readOnly = true)
public class ConsultarStockCocinaService implements ConsultarStockCocinaUseCase {

    private final RecetaRepositoryPort recetas;
    private final IngredienteRepositoryPort ingredientes;

    public ConsultarStockCocinaService(RecetaRepositoryPort recetas, IngredienteRepositoryPort ingredientes) {
        this.recetas = recetas;
        this.ingredientes = ingredientes;
    }

    @Override
    public List<StockBajo> stockBajo(Collection<Long> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            return List.of();
        }
        List<RecetaIngrediente> lineas = recetas.deProductos(productoIds.stream().distinct().toList());
        if (lineas.isEmpty()) {
            return List.of();
        }
        Map<Long, Ingrediente> porId = ingredientes.buscarPorIds(
                        lineas.stream().map(RecetaIngrediente::getIngredienteId).distinct().toList())
                .stream().collect(Collectors.toMap(Ingrediente::getId, Function.identity()));
        return lineas.stream()
                .filter(l -> porId.containsKey(l.getIngredienteId()))
                .map(l -> aviso(l, porId.get(l.getIngredienteId())))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StockBajo::productoId).thenComparing(StockBajo::insumo))
                .toList();
    }

    /** null si el insumo alcanza (está por encima de su mínimo) o ya no se usa (desactivado). */
    private static StockBajo aviso(RecetaIngrediente linea, Ingrediente insumo) {
        if (!insumo.isActivo() || !insumo.estaEnMinimo()) {
            return null;
        }
        return new StockBajo(linea.getProductoId(), insumo.getId(), insumo.getNombre(), insumo.getStockActual(),
                insumo.getStockMinimo(), insumo.getUnidad(), insumo.estaAgotado());
    }
}
