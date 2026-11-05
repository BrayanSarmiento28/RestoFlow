package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.ConsultarAvisosUseCase;
import com.udec.restoflow.application.port.in.ConsultarAvisosUseCase.AvisosDelMomento;
import com.udec.restoflow.application.port.in.ResumirAvisosIAUseCase;
import com.udec.restoflow.domain.ia.Aviso;
import com.udec.restoflow.domain.ia.RespuestaIA;
import com.udec.restoflow.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** RestoFlow AI en toda la plataforma: la campana de avisos de cada rol. */
@Tag(name = "Avisos RestoFlow AI", description = "Avisos del momento para el rol de quien consulta · todos los roles")
@RestController
@RequestMapping("/api/avisos")
public class AvisosController {

    private final ConsultarAvisosUseCase avisos;
    private final ResumirAvisosIAUseCase resumen;

    public AvisosController(ConsultarAvisosUseCase avisos, ResumirAvisosIAUseCase resumen) {
        this.avisos = avisos;
        this.resumen = resumen;
    }

    @Operation(summary = "Avisos del momento",
            description = "Cocina: platos demorados frente a su tiempo normal. Mesero: pedidos listos sin llevar y "
                    + "entregados sin cobrar (solo de sus mesas). Inventario: insumos en su mínimo o que se acaban "
                    + "en 3 días o menos y días de demanda alta. ADMIN: todos. Primero los urgentes.")
    @GetMapping
    public AvisosResponse consultar(@AuthenticationPrincipal UsuarioAutenticado actual) {
        AvisosDelMomento r = avisos.avisosPara(actual.rol(), actual.id());
        return new AvisosResponse(r.generadoEn(), r.avisos().stream().map(AvisoResponse::desde).toList());
    }

    @Operation(summary = "Resumen de los avisos escrito por la IA (Gemini)",
            description = "Qué atender primero, en 2 o 3 frases, para el rol de quien consulta. Se guarda 5 minutos "
                    + "mientras los avisos no cambien. Si Gemini no responde, el resumen se arma con reglas "
                    + "(origen REGLAS): la campana nunca se rompe por la IA.")
    @GetMapping("/resumen")
    public RespuestaIA resumen(@AuthenticationPrincipal UsuarioAutenticado actual) {
        return resumen.resumir(actual.rol(), actual.id());
    }

    /** Lo que ve el navegador (sin los datos internos de a quién va dirigido). */
    public record AvisoResponse(String clave, Aviso.Tipo tipo, Aviso.Nivel nivel, String titulo, String mensaje,
                                String enlace, LocalDateTime desde) {
        static AvisoResponse desde(Aviso a) {
            return new AvisoResponse(a.clave(), a.tipo(), a.nivel(), a.titulo(), a.mensaje(), a.enlace(), a.desde());
        }
    }

    public record AvisosResponse(LocalDateTime generadoEn, List<AvisoResponse> avisos) { }
}
