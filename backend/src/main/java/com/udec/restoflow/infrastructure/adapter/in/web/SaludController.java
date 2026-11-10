package com.udec.restoflow.infrastructure.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.udec.restoflow.infrastructure.config.RestoFlowProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Estado del servicio. Lo usa el HEALTHCHECK de Docker para saber si el backend ya está listo
 * para recibir peticiones. Es público y no expone datos del negocio.
 */
@Tag(name = "Salud", description = "Estado del servicio (usado por Docker)")
@RestController
@RequestMapping("/api/salud")
public class SaludController {

    private final String entorno;
    /** Copia del backend que respondió (en Docker, el nombre del contenedor): se ve al escalar (factor VIII). */
    private final String instancia;

    public SaludController(RestoFlowProperties propiedades) {
        this.entorno = propiedades.entorno();
        String hostname = System.getenv("HOSTNAME");
        this.instancia = hostname == null || hostname.isBlank() ? "local" : hostname;
    }

    @Operation(summary = "Estado del backend", description = "Responde 200 con {estado: UP}, el entorno y la instancia (copia del backend) que respondió.")
    @SecurityRequirements
    @GetMapping
    public Map<String, String> salud() {
        return Map.of("estado", "UP", "entorno", entorno, "instancia", instancia);
    }
}
