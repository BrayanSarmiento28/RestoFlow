package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * RestoFlow AI · con {@code restoflow.ia.historial-demo=true}, al arrancar se generan 3 semanas de ventas de ejemplo
 * con los mismos casos de uso del mesero y la cocina, para que las predicciones tengan datos en una demostración.
 */
// Base H2 propia: este contexto llena la base de ventas y no debe afectar a las demás pruebas
@SpringBootTest(properties = {"restoflow.datos-demo=true", "restoflow.ia.historial-demo=true",
        "spring.datasource.url=jdbc:h2:mem:restoflow_historial;MODE=MSSQLServer;DB_CLOSE_DELAY=-1"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HistorialDemoIntegracionTest {

    @Autowired
    private HistorialDemoConfig historial;

    @Autowired
    private PedidoRepositoryPort pedidos;

    @Autowired
    private Clock reloj;

    @Test
    @DisplayName("Genera ventas cobradas de los últimos 21 días y no las repite si ya hay ventas recientes")
    void generaTresSemanasDeVentas() {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDateTime desde = hoy.minusDays(21).atStartOfDay();
        // Las ventas se generan en un hilo aparte al arrancar: se espera a que termine
        await("el hilo de arranque").atMost(Duration.ofSeconds(60)).until(() -> Thread.getAllStackTraces().keySet()
                .stream().noneMatch(h -> h.getName().equals("restoflow-ai-historial-demo")));

        int generadas = pedidos.pagadosEntre(desde, hoy.atStartOfDay()).size();
        historial.generar();   // un segundo arranque: ya hay ventas recientes y no genera más

        assertThat(generadas).isGreaterThanOrEqualTo(21 * 8);   // al menos 8 ventas por día
        assertThat(pedidos.pagadosEntre(desde, hoy.atStartOfDay())).hasSize(generadas);
    }
}
