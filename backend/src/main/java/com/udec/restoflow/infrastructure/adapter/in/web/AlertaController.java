package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.GestionarAlertasUseCase;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.InventarioDto.AlertaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RF-07 · Alertas de bajo inventario. INVENTARIO y ADMIN. */
@Tag(name = "Alertas", description = "RF-07 Alertas de bajo inventario · INVENTARIO y ADMIN")
@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final GestionarAlertasUseCase alertas;

    public AlertaController(GestionarAlertasUseCase alertas) {
        this.alertas = alertas;
    }

    @Operation(summary = "Listar alertas", description = "De la más reciente a la más antigua.")
    @GetMapping
    public List<AlertaResponse> listar(@RequestParam(defaultValue = "true") boolean soloPendientes) {
        return alertas.listar(soloPendientes).stream().map(AlertaResponse::desde).toList();
    }

    @Operation(summary = "Marcar alerta como atendida")
    @PatchMapping("/{id}/atender")
    public AlertaResponse atender(@PathVariable Long id) {
        return AlertaResponse.desde(alertas.atender(id));
    }
}
