package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.ConsultarRegistroUseCase;
import com.udec.restoflow.application.port.in.ConsultarRegistroUseCase.ResultadoRegistro;
import com.udec.restoflow.domain.model.FiltroRegistro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Registro de actividad: cada petición al API guardada en la base de datos. Solo ADMIN. */
@Tag(name = "Registro de actividad", description = "Logs de las peticiones guardados en la base de datos · ADMIN")
@RestController
@RequestMapping("/api/registro")
public class RegistroController {

    private final ConsultarRegistroUseCase registro;

    public RegistroController(ConsultarRegistroUseCase registro) {
        this.registro = registro;
    }

    @Operation(summary = "Buscar peticiones registradas",
            description = "Del más reciente al más antiguo, con un resumen del periodo (total, rechazadas 4xx, "
                    + "errores 5xx, lentas y duración promedio). Fechas AAAA-MM-DD, las dos incluidas. "
                    + "tipo: TODAS, EXITOSAS, RECHAZADAS, ERRORES o LENTAS.")
    @GetMapping
    public ResultadoRegistro buscar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String metodo,
            @RequestParam(required = false) String ruta,
            @RequestParam(required = false) FiltroRegistro.Tipo tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        FiltroRegistro filtro = new FiltroRegistro(desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(), usuario, metodo, ruta, tipo);
        return registro.consultar(filtro, pagina, tamano);
    }
}
