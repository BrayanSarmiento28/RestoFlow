package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase;
import com.udec.restoflow.application.port.in.GenerarAlertasIAUseCase;
import com.udec.restoflow.application.port.out.AlertaRepositoryPort;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.MovimientoRepositoryPort;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.domain.ia.AnalisisVentas;
import com.udec.restoflow.domain.ia.AnalizadorAbastecimiento;
import com.udec.restoflow.domain.ia.AnalizadorVentas;
import com.udec.restoflow.domain.ia.GeneradorRecomendaciones;
import com.udec.restoflow.domain.ia.PronosticoInsumo;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.TipoMovimiento;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * RF-09 a RF-11 y RES-40 · RestoFlow AI. Este servicio solo reúne los datos por medio de los puertos; los cálculos
 * están en el dominio ({@code domain.ia}), que no depende de nada y se prueba solo.
 * <ol>
 *   <li>Ventas de las últimas 4 semanas → {@link AnalizadorVentas}.</li>
 *   <li>Salidas de inventario de las últimas 2 semanas → {@link AnalizadorAbastecimiento}, insumo por insumo.</li>
 *   <li>Las dos cosas → {@link GeneradorRecomendaciones}.</li>
 * </ol>
 * Las alertas automáticas (RES-40) se guardan como alertas normales del inventario, con el prefijo
 * "RestoFlow AI: ", para que el personal las vea donde ya revisa las demás.
 */
@Transactional(readOnly = true)
public class RestoFlowAIService implements ConsultarRestoFlowAIUseCase, GenerarAlertasIAUseCase {

    /** Primero ALTA, luego MEDIA y luego los que no necesitan compra; entre iguales, el que se acaba antes. */
    private static final Comparator<PronosticoInsumo> URGENCIA = Comparator
            .comparing(PronosticoInsumo::prioridad, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PronosticoInsumo::diasCobertura, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PronosticoInsumo::nombre);

    private final PedidoRepositoryPort pedidos;
    private final IngredienteRepositoryPort ingredientes;
    private final MovimientoRepositoryPort movimientos;
    private final AlertaRepositoryPort alertas;
    private final Clock reloj;

    public RestoFlowAIService(PedidoRepositoryPort pedidos, IngredienteRepositoryPort ingredientes,
                              MovimientoRepositoryPort movimientos, AlertaRepositoryPort alertas, Clock reloj) {
        this.pedidos = pedidos;
        this.ingredientes = ingredientes;
        this.movimientos = movimientos;
        this.alertas = alertas;
        this.reloj = reloj;
    }

    @Override
    public AnalisisRestoFlowAI analizar() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate hoy = ahora.toLocalDate();
        List<Pedido> pagados = pedidos.pagadosEntre(
                hoy.minusDays(AnalizadorVentas.SEMANAS_HISTORIAL * 7L).atStartOfDay(), hoy.atStartOfDay());
        AnalisisVentas ventas = AnalizadorVentas.analizar(pagados, hoy);
        List<PronosticoInsumo> insumos = pronosticos(hoy);
        return new AnalisisRestoFlowAI(ahora, ventas, insumos, GeneradorRecomendaciones.generar(insumos, ventas));
    }

    @Override
    @Transactional
    public ResultadoAlertas generarAlertas() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        int creadas = 0;
        int cerradas = 0;
        int yaAvisados = 0;
        for (PronosticoInsumo p : pronosticos(ahora.toLocalDate())) {
            List<Alerta> pendientes = alertas.pendientesDe(p.ingredienteId());
            List<Alerta> deIA = pendientes.stream().filter(Alerta::esDeRestoFlowAI).toList();
            if (seAcabaPronto(p)) {
                if (pendientes.isEmpty()) {
                    alertas.guardar(Alerta.nueva(p.ingredienteId(), mensaje(p), PrioridadAlerta.MEDIA, ahora));
                    Auditoria.alertaCreada(p.nombre(), PrioridadAlerta.MEDIA.name());
                    creadas++;
                } else {
                    yaAvisados++;
                }
            } else if (!p.enMinimo()) {
                // Ya alcanza para más de 3 días: la alerta anticipada cumplió su función.
                // (Si llegó a su mínimo, la alerta sigue: el control de stock decide desde ahí.)
                for (Alerta a : deIA) {
                    a.marcarAtendida(ahora);
                    alertas.guardar(a);
                    cerradas++;
                }
            }
        }
        Auditoria.alertasRestoFlowAI(creadas, cerradas);
        return new ResultadoAlertas(creadas, cerradas, yaAvisados);
    }

    private List<PronosticoInsumo> pronosticos(LocalDate hoy) {
        List<MovimientoInventario> salidas = movimientos.buscar(null, TipoMovimiento.SALIDA,
                hoy.minusDays(AnalizadorAbastecimiento.DIAS_VENTANA * 2L).atStartOfDay(), hoy.atStartOfDay());
        return ingredientes.listar(true).stream()
                .map(i -> AnalizadorAbastecimiento.analizar(i, salidas, hoy))
                .sorted(URGENCIA)
                .toList();
    }

    /**
     * Alcanza para 3 días o menos pero aún está sobre su mínimo. Cuando ya está en el mínimo, la alerta normal
     * de stock (RF-07) es la que avisa; así no hay dos alertas del mismo insumo.
     */
    private static boolean seAcabaPronto(PronosticoInsumo p) {
        return !p.enMinimo() && p.diasCobertura() != null
                && p.diasCobertura().compareTo(AnalizadorAbastecimiento.DIAS_PRIORIDAD_ALTA) <= 0;
    }

    private static String mensaje(PronosticoInsumo p) {
        String unidad = GeneradorRecomendaciones.abreviatura(p.unidad());
        return Alerta.PREFIJO_RESTOFLOW_AI + "al ritmo actual, " + p.nombre() + " alcanza para "
                + GeneradorRecomendaciones.dias(p.diasCobertura()) + ". Sugerido: comprar "
                + GeneradorRecomendaciones.numero(p.cantidadSugerida()) + " " + unidad + ".";
    }
}
