package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.DetallePedidoEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.PedidoEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.PedidoJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.ProductoJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.UsuarioJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adaptador de salida: guarda el pedido junto con sus líneas (detalle_pedidos) en SQL Server. */
@Component
public class PedidoPersistenceAdapter implements PedidoRepositoryPort {

    private final PedidoJpaRepository repositorio;
    private final UsuarioJpaRepository usuarios;
    private final ProductoJpaRepository productos;

    public PedidoPersistenceAdapter(PedidoJpaRepository repositorio, UsuarioJpaRepository usuarios,
                                    ProductoJpaRepository productos) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
        this.productos = productos;
    }

    @Override
    public Pedido guardar(Pedido pedido) {
        PedidoEntity entidad = pedido.getId() == null
                ? new PedidoEntity()
                : repositorio.findById(pedido.getId()).orElseGet(PedidoEntity::new);
        entidad.setNumeroMesa(pedido.getNumeroMesa());
        entidad.setMesero(usuarios.getReferenceById(pedido.getMeseroId()));
        entidad.setObservaciones(pedido.getObservaciones());
        entidad.setEstadoCocina(pedido.getEstadoCocina());
        entidad.setEstadoPedido(pedido.getEstadoPedido());
        entidad.setTotal(pedido.getTotal());
        entidad.setFechaCreacion(pedido.getFechaCreacion());
        entidad.setFechaInicioPreparacion(pedido.getFechaInicioPreparacion());
        entidad.setFechaListo(pedido.getFechaListo());
        entidad.setFechaEntrega(pedido.getFechaEntrega());
        entidad.setFechaCierre(pedido.getFechaCierre());

        // Las líneas sin id son nuevas (pedido nuevo o modificado): se reemplazan todas.
        // Si solo cambió el estado, las líneas se dejan como están.
        boolean lineasNuevas = pedido.getDetalles().stream().anyMatch(d -> d.getId() == null);
        if (lineasNuevas) {
            entidad.getDetalles().clear();
            for (DetallePedido detalle : pedido.getDetalles()) {
                DetallePedidoEntity linea = new DetallePedidoEntity();
                linea.setProducto(productos.getReferenceById(detalle.getProductoId()));
                linea.setCantidad(detalle.getCantidad());
                linea.setPrecioUnitario(detalle.getPrecioUnitario());
                linea.setSubtotal(detalle.getSubtotal());
                linea.setNota(detalle.getNota());
                entidad.agregarDetalle(linea);
            }
        }
        PedidoEntity guardado = repositorio.saveAndFlush(entidad);
        return PersistenceMapper.aDominio(guardado);
    }

    @Override
    public Optional<Pedido> buscarPorId(Long id) {
        return repositorio.findById(id).map(PersistenceMapper::aDominio);
    }

    @Override
    public List<Pedido> buscar(Integer numeroMesa, EstadoCocina estadoCocina, EstadoPedido estadoPedido) {
        return repositorio.buscar(numeroMesa, estadoCocina, estadoPedido).stream()
                .map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public List<Pedido> pendientesEnCocina() {
        return repositorio.pendientesEnCocina().stream().map(PersistenceMapper::aDominio).toList();
    }
}
