package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarPanelUseCase;
import com.udec.restoflow.application.port.in.ConsultarPanelUseCase.ResumenPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** RES-59 · El resumen del Dashboard se calcula una vez y se comparte durante su vigencia. */
class ConsultarPanelEnCacheServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 25);
    private static final Duration VIGENCIA = Duration.ofSeconds(5);

    private Dobles.RelojManual reloj;
    private CalculoContado calculo;

    /** Cálculo de mentira que cuenta cuántas veces se ejecutó. */
    private static class CalculoContado implements ConsultarPanelUseCase {
        final AtomicInteger veces = new AtomicInteger();
        final CountDownLatch nuncaLlega = new CountDownLatch(1);
        long demoraMs;

        @Override
        public ResumenPanel resumen(LocalDate dia) {
            int numero = veces.incrementAndGet();
            if (demoraMs > 0) {
                try {
                    // Simula un cálculo lento: espera demoraMs a una señal que nunca llega
                    nuncaLlega.await(demoraMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            // pedidosPagados = número de cálculo, para saber cuál resumen se recibió
            return new ResumenPanel(dia, BigDecimal.ZERO, numero, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0,
                    List.of(), List.of(), List.of(), 0, 0, List.of(), List.of());
        }
    }

    @BeforeEach
    void preparar() {
        reloj = new Dobles.RelojManual();
        calculo = new CalculoContado();
    }

    @Test
    @DisplayName("Dentro de la vigencia se reutiliza el mismo resumen sin volver a calcularlo")
    void reutilizaDentroDeLaVigencia() {
        var panel = new ConsultarPanelEnCacheService(calculo, VIGENCIA, reloj);

        ResumenPanel primero = panel.resumen(HOY);
        reloj.avanzarSegundos(4);
        ResumenPanel segundo = panel.resumen(HOY);

        assertThat(segundo).isSameAs(primero);
        assertThat(calculo.veces).hasValue(1);
    }

    @Test
    @DisplayName("Cuando vence la vigencia se recalcula, así las cifras siguen al día")
    void recalculaAlVencer() {
        var panel = new ConsultarPanelEnCacheService(calculo, VIGENCIA, reloj);

        panel.resumen(HOY);
        reloj.avanzarSegundos(5);

        assertThat(panel.resumen(HOY).pedidosPagados()).isEqualTo(2);
        assertThat(calculo.veces).hasValue(2);
    }

    @Test
    @DisplayName("El resumen de otro día no se confunde con el guardado")
    void otroDiaSeCalculaAparte() {
        var panel = new ConsultarPanelEnCacheService(calculo, VIGENCIA, reloj);

        panel.resumen(HOY);
        ResumenPanel ayer = panel.resumen(HOY.minusDays(1));

        assertThat(ayer.dia()).isEqualTo(HOY.minusDays(1));
        assertThat(calculo.veces).hasValue(2);
    }

    @Test
    @DisplayName("Con vigencia 0 siempre se recalcula (así lo usan las pruebas de integración)")
    void sinVigenciaSiempreRecalcula() {
        var panel = new ConsultarPanelEnCacheService(calculo, Duration.ZERO, reloj);

        panel.resumen(HOY);
        panel.resumen(HOY);

        assertThat(calculo.veces).hasValue(2);
    }

    @Test
    @DisplayName("40 administradores a la vez: el resumen se calcula una sola vez y todos reciben el mismo")
    void muchosALaVezCalculanUnaVez() throws Exception {
        var panel = new ConsultarPanelEnCacheService(calculo, VIGENCIA, reloj);
        calculo.demoraMs = 200;   // un cálculo lento, como con miles de pedidos en el día
        int administradores = 40;

        ExecutorService hilos = Executors.newFixedThreadPool(administradores);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<ResumenPanel>> respuestas = new ArrayList<>();
        for (int i = 0; i < administradores; i++) {
            respuestas.add(hilos.submit(() -> {
                salida.await();
                return panel.resumen(HOY);
            }));
        }
        salida.countDown();
        List<ResumenPanel> resumenes = new ArrayList<>();
        for (Future<ResumenPanel> r : respuestas) {
            resumenes.add(r.get(30, TimeUnit.SECONDS));
        }
        hilos.shutdown();

        assertThat(calculo.veces).hasValue(1);
        assertThat(resumenes).allSatisfy(r -> assertThat(r).isSameAs(resumenes.get(0)));
    }
}
