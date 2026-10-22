package com.udec.restoflow.infrastructure;

import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.application.port.out.RecetaRepositoryPort;
import com.udec.restoflow.domain.model.Producto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Factor XII (procesos de administración): con restoflow.datos-demo=true, el primer arranque carga el menú y
 * el inventario de ejemplo con el mismo código de la aplicación (desarrollo y pre-producción).
 */
// Base H2 propia: al cerrar este contexto se borran sus tablas y no deben ser las de las demás pruebas
@SpringBootTest(properties = {"restoflow.datos-demo=true",
        "spring.datasource.url=jdbc:h2:mem:restoflow_demo;MODE=MSSQLServer;DB_CLOSE_DELAY=-1"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatosDemoIntegracionTest {

    @Autowired
    private ProductoRepositoryPort productos;

    @Autowired
    private IngredienteRepositoryPort ingredientes;

    @Autowired
    private RecetaRepositoryPort recetas;

    @Test
    @DisplayName("Se cargan productos, insumos y recetas de ejemplo")
    void datosDeEjemplo() {
        List<Producto> menu = productos.listar(true, null);

        assertThat(menu).isNotEmpty();
        assertThat(ingredientes.contar()).isPositive();
        assertThat(recetas.deProductos(menu.stream().map(Producto::getId).toList())).isNotEmpty();
    }
}
