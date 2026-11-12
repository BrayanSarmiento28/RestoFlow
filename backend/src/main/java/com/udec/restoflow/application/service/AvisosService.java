package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarAvisosUseCase;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase.AnalisisRestoFlowAI;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.Aviso;
import com.udec.restoflow.domain.ia.GeneradorAvisos;
import com.udec.restoflow.domain.ia.PronosticoInsumo;
import com.udec.restoflow.domain.ia.TiemposPreparacion;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Rol;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RestoFlow AI integrado en toda la plataforma (recomendación del comité): reúne los datos por los puertos y deja
 * las reglas al dominio ({@link GeneradorAvisos}). Solo calcula lo que el rol necesita: a un mesero no se le hace
 * el análisis de inventario.
 */
@Transactional(readOnly = true)
public class AvisosService implements ConsultarAvisosUseCase {

    /** Días de pedidos de los que se aprende el tiempo normal de cada plato. */
    static final int DIAS_TIEMPOS = 14;

    private final PedidoRepositoryPort pedidos;
    private final ConsultarRestoFlowAIUseCase restoFlowAI;
    private final Clock reloj;

    public AvisosService(PedidoRepositoryPort pedidos, ConsultarRestoFlowAIUseCase restoFlowAI, Clock reloj) {
        this.pedidos = pedidos;
        this.restoFlowAI = restoFlowAI;
        this.reloj = reloj;
    }

    @Override
    public AvisosDelMomento avisosPara(Rol rol, Long usuarioId) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<Pedido> abiertos = pedidos.buscar(null, null, EstadoPedido.ABIERTO);

        TiemposPreparacion tiempos = TiemposPreparacion.aprender(
                hayEnCocina(abiertos) ? pedidos.creadosEntre(ahora.minusDays(DIAS_TIEMPOS), ahora, null) : List.of());

        List<PronosticoInsumo> insumos = List.of();
        Prediccion prediccion = null;
        if (rol != Rol.MESERO) {
            AnalisisRestoFlowAI analisis = restoFlowAI.analizar();
            insumos = analisis.insumos();
            prediccion = analisis.ventas().prediccion();
        }

        List<Aviso> avisos = GeneradorAvisos.generar(abiertos, tiempos, insumos, prediccion, ahora).stream()
                .filter(a -> a.esPara(rol, usuarioId))
                .toList();
        return new AvisosDelMomento(ahora, avisos);
    }

    private static boolean hayEnCocina(List<Pedido> abiertos) {
        return abiertos.stream().anyMatch(p -> p.getEstadoCocina() == EstadoCocina.PENDIENTE
                || p.getEstadoCocina() == EstadoCocina.EN_PREPARACION);
    }
}
