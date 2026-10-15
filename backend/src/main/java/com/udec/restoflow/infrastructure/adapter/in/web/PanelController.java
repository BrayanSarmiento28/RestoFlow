package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.ConsultarPanelUseCase;
import com.udec.restoflow.application.port.in.ConsultarPanelUseCase.ResumenPanel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/** RF-08 · Panel administrativo. Solo ADMIN. */
@Tag(name = "Panel", description = "RF-08 Resumen de ventas, pedidos e inventario · ADMIN")
@RestController
@RequestMapping("/api/panel")
public class PanelController {

    private final ConsultarPanelUseCase panel;
    private final Clock reloj;

    public PanelController(ConsultarPanelUseCase panel, Clock reloj) {
        this.panel = panel;
        this.reloj = reloj;
    }

    @Operation(summary = "Resumen del día",
            description = "Ventas (pedidos pagados), comparación con el día anterior, pedidos abiertos y en cocina, "
                    + "ventas por hora, los 5 productos más vendidos e insumos en su mínimo. Sin fecha: hoy.")
    @GetMapping("/resumen")
    public ResumenPanel resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return panel.resumen(fecha != null ? fecha : LocalDate.now(reloj));
    }
}
