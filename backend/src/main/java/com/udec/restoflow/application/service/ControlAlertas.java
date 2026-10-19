package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.out.AlertaRepositoryPort;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.UnidadMedida;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-07 · Decide cuándo crear o cerrar alertas de bajo inventario. Se llama después de cada cambio de stock.
 * <ul>
 *   <li>Llegó al mínimo y no tenía alerta pendiente → alerta MEDIA.</li>
 *   <li>Se agotó (stock 0) → alerta ALTA (reemplaza la MEDIA pendiente).</li>
 *   <li>Volvió a estar por encima del mínimo → sus alertas pendientes se marcan como atendidas solas.</li>
 * </ul>
 * Así nunca hay dos alertas pendientes iguales para el mismo insumo.
 */
public class ControlAlertas {

    private final AlertaRepositoryPort alertas;
    private final Clock reloj;

    public ControlAlertas(AlertaRepositoryPort alertas, Clock reloj) {
        this.alertas = alertas;
        this.reloj = reloj;
    }

    public void revisar(Ingrediente ingrediente) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<Alerta> pendientes = alertas.pendientesDe(ingrediente.getId());

        if (!ingrediente.estaEnMinimo()) {
            pendientes.forEach(a -> {
                a.marcarAtendida(ahora);
                alertas.guardar(a);
            });
            return;
        }
        PrioridadAlerta prioridad = ingrediente.estaAgotado() ? PrioridadAlerta.ALTA : PrioridadAlerta.MEDIA;
        boolean yaAvisada = pendientes.stream().anyMatch(a -> a.getPrioridad() == prioridad
                || a.getPrioridad() == PrioridadAlerta.ALTA);
        if (yaAvisada) {
            return;
        }
        pendientes.forEach(a -> {               // la MEDIA anterior queda reemplazada por la ALTA
            a.marcarAtendida(ahora);
            alertas.guardar(a);
        });
        String unidad = abreviatura(ingrediente.getUnidad());
        String mensaje = ingrediente.estaAgotado()
                ? "Se agotó " + ingrediente.getNombre() + ". Registre una entrada de mercancía."
                : "Quedan " + numero(ingrediente.getStockActual()) + " " + unidad + " de " + ingrediente.getNombre()
                  + " (mínimo " + numero(ingrediente.getStockMinimo()) + " " + unidad + ").";
        alertas.guardar(Alerta.nueva(ingrediente.getId(), mensaje, prioridad, ahora));
    }

    /** RF-06 · Se vendió más de lo que había registrado: hay una diferencia en el inventario. */
    public void faltante(Ingrediente ingrediente, BigDecimal faltante, Long pedidoId) {
        String mensaje = "La venta del pedido #" + pedidoId + " necesitaba " + numero(faltante) + " "
                + abreviatura(ingrediente.getUnidad()) + " más de " + ingrediente.getNombre()
                + " de lo registrado. Revise el inventario con un conteo físico.";
        alertas.guardar(Alerta.nueva(ingrediente.getId(), mensaje, PrioridadAlerta.ALTA, LocalDateTime.now(reloj)));
    }

    /** 600 g, 2 und, 1,5 l: igual que en las pantallas. */
    static String abreviatura(UnidadMedida unidad) {
        return switch (unidad) {
            case GRAMO -> "g";
            case KILOGRAMO -> "kg";
            case MILILITRO -> "ml";
            case LITRO -> "l";
            case UNIDAD -> "und";
        };
    }

    static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
