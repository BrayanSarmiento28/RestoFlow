package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase.AnalisisRestoFlowAI;
import com.udec.restoflow.application.port.in.GenerarAlertasIAUseCase.ResultadoAlertas;
import com.udec.restoflow.domain.ia.Recomendacion;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RES-36 a RES-41 · RestoFlow AI con dobles en memoria (sin base de datos), como pide RES-44.
 * Hoy es jueves 29 de octubre de 2026 a las 10:00; el consumo se mide con los 7 días completos anteriores.
 */
class RestoFlowAIServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);

    private DoblesInventario.IngredientesEnMemoria ingredientes;
    private DoblesInventario.MovimientosEnMemoria movimientos;
    private DoblesInventario.AlertasEnMemoria alertas;
    private RestoFlowAIService ia;
    private ControlAlertas control;
    private Long arrozId;

    @BeforeEach
    void preparar() {
        ingredientes = new DoblesInventario.IngredientesEnMemoria();
        movimientos = new DoblesInventario.MovimientosEnMemoria();
        alertas = new DoblesInventario.AlertasEnMemoria();
        Clock reloj = Clock.fixed(HOY.atTime(10, 0).atZone(BOGOTA).toInstant(), BOGOTA);
        ia = new RestoFlowAIService(new DoblesPedidos.PedidosEnMemoria(), ingredientes, movimientos, alertas, reloj);
        control = new ControlAlertas(alertas, reloj);

        // Arroz: hay 2.500 g, el mínimo es 1.000 g y se gastan 1.000 g al día → alcanza para 2,5 días
        arrozId = ingredientes.guardar(new Ingrediente(null, "Arroz", UnidadMedida.GRAMO, new BigDecimal("2500"),
                new BigDecimal("1000"), true)).getId();
        ingredientes.guardar(new Ingrediente(null, "Sal", UnidadMedida.GRAMO, new BigDecimal("5000"),
                new BigDecimal("500"), true));
        for (int i = 1; i <= 7; i++) {                                                    // los 7 días completos anteriores
            movimientos.guardar(new MovimientoInventario(null, arrozId, TipoMovimiento.SALIDA, new BigDecimal("1000"),
                    null, null, null, HOY.minusDays(i).atTime(13, 0)));
        }
    }

    @Test
    @DisplayName("RF-09 a RF-11 · El análisis ordena los insumos por urgencia y recomienda comprar")
    void analisis() {
        AnalisisRestoFlowAI analisis = ia.analizar();

        assertEquals(LocalDateTime.of(2026, 10, 29, 10, 0), analisis.generadoEn());
        assertEquals("Arroz", analisis.insumos().get(0).nombre());                    // el más urgente primero
        assertEquals(PrioridadAlerta.ALTA, analisis.insumos().get(0).prioridad());
        assertNull(analisis.insumos().get(1).prioridad());                             // la sal alcanza

        Recomendacion primera = analisis.recomendaciones().get(0);
        assertEquals(Recomendacion.Tipo.COMPRA, primera.tipo());
        assertEquals("Comprar 5.500 g de Arroz", primera.titulo());                     // 7.000 + 1.000 − 2.500
        // Sin ventas todavía: RestoFlow AI avisa que está aprendiendo y no predice
        assertNull(analisis.ventas().prediccion());
        assertTrue(analisis.recomendaciones().stream().anyMatch(r -> r.tipo() == Recomendacion.Tipo.INFORMACION));
    }

    @Test
    @DisplayName("RES-40 · Crea una alerta anticipada cuando un insumo alcanza para 3 días o menos, sin repetirla")
    void alertaAnticipada() {
        ResultadoAlertas primera = ia.generarAlertas();
        ResultadoAlertas segunda = ia.generarAlertas();

        assertEquals(new ResultadoAlertas(1, 0, 0), primera);
        assertEquals(new ResultadoAlertas(0, 0, 1), segunda);                          // no se duplica: ya estaba avisado
        Alerta alerta = alertas.pendientes().get(0);
        assertTrue(alerta.esDeRestoFlowAI());
        assertEquals(arrozId, alerta.getIngredienteId());
        assertEquals(PrioridadAlerta.MEDIA, alerta.getPrioridad());
        assertEquals("RestoFlow AI: al ritmo actual, Arroz alcanza para 2,5 días. Sugerido: comprar 5.500 g.",
                alerta.getMensaje());
    }

    @Test
    @DisplayName("RES-40 · Una venta no cierra la alerta anticipada; una compra suficiente sí")
    void cierreDeLaAlerta() {
        ia.generarAlertas();

        // Se vende algo, pero el arroz sigue sobre su mínimo: el control de stock no debe cerrar la alerta de la IA
        Ingrediente arroz = ingredientes.buscarPorId(arrozId).orElseThrow();
        arroz.consumir(new BigDecimal("300"));
        control.revisar(ingredientes.guardar(arroz));
        assertEquals(1, alertas.pendientes().size());

        // Llega una compra: ahora alcanza para mucho más, y la IA cierra su propia alerta
        Ingrediente comprado = ingredientes.buscarPorId(arrozId).orElseThrow();
        comprado.ingresar(new BigDecimal("20000"));
        ingredientes.guardar(comprado);

        assertEquals(new ResultadoAlertas(0, 1, 0), ia.generarAlertas());
        assertTrue(alertas.pendientes().isEmpty());
    }

    @Test
    @DisplayName("RES-40 · Si el insumo ya está en su mínimo, avisa la alerta normal de stock y la IA no crea otra")
    void enMinimoAvisaElControlDeStock() {
        Ingrediente arroz = ingredientes.buscarPorId(arrozId).orElseThrow();
        arroz.consumir(new BigDecimal("1800"));                                         // quedan 700 g < 1.000
        control.revisar(ingredientes.guardar(arroz));

        assertEquals(new ResultadoAlertas(0, 0, 0), ia.generarAlertas());
        assertEquals(1, alertas.pendientes().size());
        assertFalse(alertas.pendientes().get(0).esDeRestoFlowAI());
    }
}
