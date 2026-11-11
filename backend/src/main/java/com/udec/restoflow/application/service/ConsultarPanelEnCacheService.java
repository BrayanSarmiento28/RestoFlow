package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarPanelUseCase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * RES-59 · El resumen del Dashboard se calcula como máximo una vez por {@code vigencia} y se comparte.
 * <p>
 * Calcular el resumen recorre todos los pedidos del día (ventas, por hora, por mesero, más vendidos…), así que
 * cuesta más a medida que avanza el día. En la prueba de estrés, 40 administradores lo pedían cada 5 segundos y
 * ocupaban todas las conexiones a la base de datos: el inicio de sesión y el menú se quedaban esperando.
 * <p>
 * Este servicio envuelve al {@link ConsultarPanelService} real (patrón <i>decorador</i>): el primero que pide el
 * resumen lo calcula y los demás, durante la vigencia, reciben esa misma respuesta. Si llegan varios a la vez
 * cuando el resumen venció, solo uno lo calcula ({@code synchronized}) y los otros reciben su resultado, en vez
 * de calcularlo todos al mismo tiempo. Con 5 segundos de vigencia las cifras siguen al día: el Dashboard del
 * frontend se actualiza cada 10 segundos.
 */
public class ConsultarPanelEnCacheService implements ConsultarPanelUseCase {

    private final ConsultarPanelUseCase calculo;
    private final Duration vigencia;
    private final Clock reloj;

    /** Último resumen calculado (uno solo: casi siempre se pide el de hoy). */
    private Guardado guardado;

    public ConsultarPanelEnCacheService(ConsultarPanelUseCase calculo, Duration vigencia, Clock reloj) {
        this.calculo = calculo;
        this.vigencia = vigencia;
        this.reloj = reloj;
    }

    @Override
    public synchronized ResumenPanel resumen(LocalDate dia) {
        Instant ahora = reloj.instant();
        if (guardado != null && guardado.dia().equals(dia) && ahora.isBefore(guardado.vence())) {
            return guardado.resumen();
        }
        ResumenPanel resumen = calculo.resumen(dia);
        if (!vigencia.isZero() && !vigencia.isNegative()) {
            guardado = new Guardado(dia, resumen, ahora.plus(vigencia));
        }
        return resumen;
    }

    private record Guardado(LocalDate dia, ResumenPanel resumen, Instant vence) {
    }
}
