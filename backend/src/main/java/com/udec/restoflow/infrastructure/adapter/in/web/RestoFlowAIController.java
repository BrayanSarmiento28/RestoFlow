package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase.AnalisisRestoFlowAI;
import com.udec.restoflow.application.port.in.GenerarAlertasIAUseCase;
import com.udec.restoflow.application.port.in.GenerarAlertasIAUseCase.ResultadoAlertas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-09 a RF-11 · RestoFlow AI. Administrador e inventario. */
@Tag(name = "RestoFlow AI", description = "RF-09 a RF-11 Análisis, tendencias, predicción y recomendaciones · ADMIN e INVENTARIO")
@RestController
@RequestMapping("/api/restoflow-ai")
public class RestoFlowAIController {

    private final ConsultarRestoFlowAIUseCase analisis;
    private final GenerarAlertasIAUseCase alertas;

    public RestoFlowAIController(ConsultarRestoFlowAIUseCase analisis, GenerarAlertasIAUseCase alertas) {
        this.analisis = analisis;
        this.alertas = alertas;
    }

    @Operation(summary = "Análisis de RestoFlow AI",
            description = "Ventas de las últimas 4 semanas (más vendidos, día fuerte, hora pico, tendencia y predicción "
                    + "de mañana), consumo de cada insumo (días que alcanza, tendencia y cuánto comprar) y "
                    + "recomendaciones ordenadas por prioridad. Se calcula al momento con los datos actuales.")
    @GetMapping("/analisis")
    public AnalisisRestoFlowAI analisis() {
        return analisis.analizar();
    }

    @Operation(summary = "Generar alertas ahora",
            description = "Hace lo mismo que la tarea programada de cada hora: crea alertas para los insumos que "
                    + "alcanzan para 3 días o menos y cierra las que ya no hacen falta.")
    @PostMapping("/alertas")
    public ResultadoAlertas generarAlertas() {
        return alertas.generarAlertas();
    }
}
