package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.in.GestionarCatalogoUseCase;
import com.udec.restoflow.application.port.in.GestionarCatalogoUseCase.ComandoProducto;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.domain.model.Categoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Carga un menú de ejemplo para poder probar pedidos y cocina desde el primer arranque.
 * Solo en desarrollo y pre-producción (restoflow.datos-demo=true) y solo si no hay productos.
 */
@Component
@ConditionalOnProperty(name = "restoflow.datos-demo", havingValue = "true")
public class MenuInicialConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MenuInicialConfig.class);

    private final GestionarCatalogoUseCase catalogo;
    private final ProductoRepositoryPort productos;

    public MenuInicialConfig(GestionarCatalogoUseCase catalogo, ProductoRepositoryPort productos) {
        this.catalogo = catalogo;
        this.productos = productos;
    }

    @Override
    public void run(String... args) {
        if (productos.contar() > 0) {
            return;
        }
        Categoria entradas = catalogo.crearCategoria("Entradas");
        Categoria platos = catalogo.crearCategoria("Platos fuertes");
        Categoria bebidas = catalogo.crearCategoria("Bebidas");
        Categoria postres = catalogo.crearCategoria("Postres");

        producto("Empanadas (3 und)", "Empanadas de carne con ají", "9000", entradas);
        producto("Patacón con hogao", "Patacón crocante con hogao y queso", "12000", entradas);
        producto("Bandeja paisa", "Frijoles, arroz, carne molida, chicharrón, huevo y aguacate", "32000", platos);
        producto("Ajiaco santafereño", "Con pollo, mazorca, crema y alcaparras", "28000", platos);
        producto("Pechuga a la plancha", "Con papa a la francesa y ensalada", "26000", platos);
        producto("Limonada de coco", null, "9000", bebidas);
        producto("Jugo natural", "Mora, lulo o maracuyá", "7000", bebidas);
        producto("Gaseosa", null, "5000", bebidas);
        producto("Postre de natas", null, "8000", postres);
        log.info("Se cargó el menú de ejemplo (4 categorías, 9 productos).");
    }

    private void producto(String nombre, String descripcion, String precio, Categoria categoria) {
        catalogo.crearProducto(new ComandoProducto(nombre, descripcion, new BigDecimal(precio), categoria.getId()));
    }
}
