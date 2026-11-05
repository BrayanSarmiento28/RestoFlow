package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.ConsultarAsistenteIAUseCase;
import com.udec.restoflow.application.port.in.ConsultarAsistenteIAUseCase.RecomendacionesIA;
import com.udec.restoflow.domain.ia.RespuestaIA;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RestoFlow AI con IA externa (Google Gemini): preguntas del administrador y recomendaciones redactadas. */
@Tag(name = "RestoFlow AI · Gemini", description = "IA externa: preguntas (ADMIN) y recomendaciones (ADMIN e INVENTARIO)")
@RestController
@RequestMapping("/api/restoflow-ai")
public class AsistenteIAController {

    private final ConsultarAsistenteIAUseCase asistente;

    public AsistenteIAController(ConsultarAsistenteIAUseCase asistente) {
        this.asistente = asistente;
    }

    @Operation(summary = "Pregúntale a RestoFlow AI (solo ADMIN)",
            description = "Gemini responde con los datos reales del restaurante (ventas, productos, insumos, predicción "
                    + "y avisos; nunca datos personales). 400 si la pregunta está vacía o pasa de 500 caracteres; "
                    + "503 si Gemini no está disponible o no hay clave configurada.")
    @PostMapping("/preguntar")
    public RespuestaIA preguntar(@Valid @RequestBody PreguntaRequest request) {
        return asistente.preguntar(request.pregunta());
    }

    @Operation(summary = "Recomendaciones escritas por la IA",
            description = "Gemini analiza los datos y propone de 3 a 5 recomendaciones. Se guardan 30 minutos. Si Gemini "
                    + "no responde, se devuelven las calculadas con reglas (origen REGLAS).")
    @GetMapping("/recomendaciones-ia")
    public RecomendacionesIA recomendaciones() {
        return asistente.recomendaciones();
    }

    public record PreguntaRequest(
            @NotBlank(message = "Escriba una pregunta")
            @Size(max = 500, message = "La pregunta admite máximo 500 caracteres") String pregunta) {
    }
}
