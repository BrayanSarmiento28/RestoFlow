package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCocinaUseCase;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.Pedido;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** RF-03 y RF-04 · La cocina ve las comandas en orden de llegada y avanza su estado. */
@Transactional
public class GestionarCocinaService implements GestionarCocinaUseCase {

    private final PedidoRepositoryPort pedidos;
    private final Clock reloj;

    public GestionarCocinaService(PedidoRepositoryPort pedidos, Clock reloj) {
        this.pedidos = pedidos;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> comandasPendientes() {
        return pedidos.pendientesEnCocina();
    }

    @Override
    public Pedido cambiarEstado(Long pedidoId, EstadoCocina nuevoEstado) {
        Pedido pedido = pedidos.buscarPorId(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", pedidoId));
        pedido.cambiarEstadoCocina(nuevoEstado, LocalDateTime.now(reloj));
        return pedidos.guardar(pedido);
    }
}
