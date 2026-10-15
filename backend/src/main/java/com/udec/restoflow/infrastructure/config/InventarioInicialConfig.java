package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.in.GestionarCatalogoUseCase;
import com.udec.restoflow.application.port.in.GestionarInventarioUseCase;
import com.udec.restoflow.application.port.in.GestionarInventarioUseCase.ComandoIngrediente;
import com.udec.restoflow.application.port.in.GestionarRecetasUseCase;
import com.udec.restoflow.application.port.in.GestionarRecetasUseCase.LineaReceta;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Carga insumos y recetas de ejemplo para probar el descuento automático (RF-06) y las alertas (RF-07).
 * Solo con restoflow.datos-demo=true, solo si no hay insumos, y después del menú de ejemplo (@Order 2).
 * Algunos insumos arrancan cerca de su mínimo a propósito, para ver alertas desde el primer día.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "restoflow.datos-demo", havingValue = "true")
public class InventarioInicialConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InventarioInicialConfig.class);

    private final GestionarInventarioUseCase inventario;
    private final GestionarRecetasUseCase recetas;
    private final GestionarCatalogoUseCase catalogo;
    private final IngredienteRepositoryPort ingredientes;

    public InventarioInicialConfig(GestionarInventarioUseCase inventario, GestionarRecetasUseCase recetas,
                                   GestionarCatalogoUseCase catalogo, IngredienteRepositoryPort ingredientes) {
        this.inventario = inventario;
        this.recetas = recetas;
        this.catalogo = catalogo;
        this.ingredientes = ingredientes;
    }

    @Override
    public void run(String... args) {
        if (ingredientes.contar() > 0) {
            return;
        }
        Map<String, Long> id = new HashMap<>();
        insumo(id, "Arroz", UnidadMedida.GRAMO, "12000", "3000");
        insumo(id, "Fríjol", UnidadMedida.GRAMO, "6000", "2000");
        insumo(id, "Carne molida", UnidadMedida.GRAMO, "5000", "1500");
        insumo(id, "Chicharrón", UnidadMedida.GRAMO, "1800", "1500");
        insumo(id, "Huevo", UnidadMedida.UNIDAD, "60", "24");
        insumo(id, "Aguacate", UnidadMedida.UNIDAD, "10", "6");
        insumo(id, "Pechuga de pollo", UnidadMedida.GRAMO, "8000", "2500");
        insumo(id, "Papa", UnidadMedida.GRAMO, "15000", "4000");
        insumo(id, "Mazorca", UnidadMedida.UNIDAD, "20", "8");
        insumo(id, "Plátano verde", UnidadMedida.UNIDAD, "25", "10");
        insumo(id, "Harina de maíz", UnidadMedida.GRAMO, "4000", "1000");
        insumo(id, "Limón", UnidadMedida.UNIDAD, "40", "15");
        insumo(id, "Leche de coco", UnidadMedida.MILILITRO, "2000", "1000");
        insumo(id, "Pulpa de fruta", UnidadMedida.GRAMO, "3000", "1000");
        insumo(id, "Gaseosa 350 ml", UnidadMedida.UNIDAD, "48", "12");
        insumo(id, "Leche", UnidadMedida.MILILITRO, "6000", "2000");

        Map<String, Long> productos = catalogo.listarProductos(false, null).stream()
                .collect(Collectors.toMap(Producto::getNombre, Producto::getId, (a, b) -> a));
        int conReceta = 0;
        conReceta += receta(productos, id, "Bandeja paisa", "Arroz", "150", "Fríjol", "120", "Carne molida", "100",
                "Chicharrón", "80", "Huevo", "1", "Aguacate", "0.25", "Plátano verde", "0.5");
        conReceta += receta(productos, id, "Ajiaco santafereño", "Pechuga de pollo", "120", "Papa", "300",
                "Mazorca", "0.5", "Arroz", "80");
        conReceta += receta(productos, id, "Pechuga a la plancha", "Pechuga de pollo", "250", "Papa", "200");
        conReceta += receta(productos, id, "Empanadas (3 und)", "Harina de maíz", "150", "Carne molida", "60",
                "Limón", "0.5");
        conReceta += receta(productos, id, "Patacón con hogao", "Plátano verde", "1");
        conReceta += receta(productos, id, "Limonada de coco", "Limón", "2", "Leche de coco", "120");
        conReceta += receta(productos, id, "Jugo natural", "Pulpa de fruta", "150");
        conReceta += receta(productos, id, "Gaseosa", "Gaseosa 350 ml", "1");
        conReceta += receta(productos, id, "Postre de natas", "Leche", "250", "Huevo", "1");
        log.info("Se cargó el inventario de ejemplo ({} insumos, {} productos con receta).", id.size(), conReceta);
    }

    private void insumo(Map<String, Long> ids, String nombre, UnidadMedida unidad, String stock, String minimo) {
        Ingrediente creado = inventario.crear(new ComandoIngrediente(nombre, unidad, new BigDecimal(minimo),
                new BigDecimal(stock)), null);
        ids.put(nombre, creado.getId());
    }

    /** Pares nombre de insumo / cantidad. Si el producto no existe (menú propio), se omite. */
    private int receta(Map<String, Long> productos, Map<String, Long> insumos, String producto, String... pares) {
        Long productoId = productos.get(producto);
        if (productoId == null) {
            return 0;
        }
        List<LineaReceta> lineas = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            lineas.add(new LineaReceta(insumos.get(pares[i]), new BigDecimal(pares[i + 1])));
        }
        recetas.definir(productoId, lineas);
        return 1;
    }
}
