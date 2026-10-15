package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.MovimientoRepositoryPort;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.MovimientoInventarioEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.IngredienteJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.MovimientoInventarioJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.PedidoJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Adaptador de salida: historial de movimientos en la tabla "movimientos_inventario". */
@Component
public class MovimientoPersistenceAdapter implements MovimientoRepositoryPort {

    private final MovimientoInventarioJpaRepository repositorio;
    private final IngredienteJpaRepository ingredientes;
    private final UsuarioJpaRepository usuarios;
    private final PedidoJpaRepository pedidos;

    public MovimientoPersistenceAdapter(MovimientoInventarioJpaRepository repositorio,
                                        IngredienteJpaRepository ingredientes, UsuarioJpaRepository usuarios,
                                        PedidoJpaRepository pedidos) {
        this.repositorio = repositorio;
        this.ingredientes = ingredientes;
        this.usuarios = usuarios;
        this.pedidos = pedidos;
    }

    @Override
    public MovimientoInventario guardar(MovimientoInventario m) {
        MovimientoInventarioEntity e = new MovimientoInventarioEntity();
        e.setIngrediente(ingredientes.getReferenceById(m.getIngredienteId()));
        e.setTipo(m.getTipo());
        e.setCantidad(m.getCantidad());
        e.setMotivo(m.getMotivo());
        e.setUsuario(m.getUsuarioId() == null ? null : usuarios.getReferenceById(m.getUsuarioId()));
        e.setPedido(m.getPedidoId() == null ? null : pedidos.getReferenceById(m.getPedidoId()));
        e.setFecha(m.getFecha());
        MovimientoInventarioEntity guardado = repositorio.save(e);
        return new MovimientoInventario(guardado.getId(), m.getIngredienteId(), m.getTipo(), m.getCantidad(),
                m.getMotivo(), m.getUsuarioId(), m.getPedidoId(), m.getFecha());
    }

    @Override
    public List<MovimientoInventario> buscar(Long ingredienteId, TipoMovimiento tipo, LocalDateTime desde,
                                             LocalDateTime hasta) {
        return repositorio.buscar(ingredienteId, tipo, desde, hasta).stream().map(PersistenceMapper::aDominio).toList();
    }
}
