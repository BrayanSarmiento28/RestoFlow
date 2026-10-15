package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarHistorialUseCase;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** RF-12 · Historial de pedidos y de ventas por rango de fechas. */
@Transactional(readOnly = true)
public class ConsultarHistorialService implements ConsultarHistorialUseCase {

    /** Evita consultas enormes por error (por ejemplo, un año entero desde el navegador). */
    static final int MAXIMO_DIAS = 92;

    private final PedidoRepositoryPort pedidos;

    public ConsultarHistorialService(PedidoRepositoryPort pedidos) {
        this.pedidos = pedidos;
    }

    @Override
    public List<Pedido> pedidos(LocalDate desde, LocalDate hasta, EstadoPedido estado) {
        validar(desde, hasta);
        return pedidos.creadosEntre(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay(), estado);
    }

    @Override
    public ResumenVentas ventas(LocalDate desde, LocalDate hasta) {
        validar(desde, hasta);
        List<Pedido> ventas = pedidos.pagadosEntre(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
        Map<LocalDate, List<Pedido>> grupos = new TreeMap<>();
        ventas.forEach(p -> grupos.computeIfAbsent(p.getFechaCierre().toLocalDate(), d -> new ArrayList<>()).add(p));
        List<VentaPorDia> porDia = grupos.entrySet().stream()
                .map(e -> new VentaPorDia(e.getKey(), ConsultarPanelService.sumar(e.getValue()), e.getValue().size()))
                .toList();
        BigDecimal total = ConsultarPanelService.sumar(ventas);
        return new ResumenVentas(desde, hasta, total, ventas.size(),
                ConsultarPanelService.promedio(total, ventas.size()), porDia, ventas);
    }

    private static void validar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new DomainException("Indique la fecha inicial y la final");
        }
        if (hasta.isBefore(desde)) {
            throw new DomainException("La fecha final no puede ser anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS) {
            throw new DomainException("Consulte máximo " + MAXIMO_DIAS + " días a la vez");
        }
    }
}
