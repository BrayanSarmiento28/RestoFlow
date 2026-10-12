package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarPedidosUseCase;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.Usuario;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-02 · El mesero registra, modifica, cancela y cierra (venta) los pedidos.
 * Las reglas de cuándo se puede hacer cada cosa están en la entidad Pedido; este servicio
 * solo busca los datos, llama a la regla y guarda el resultado.
 */
@Transactional
public class GestionarPedidosService implements GestionarPedidosUseCase {

    private final PedidoRepositoryPort pedidos;
    private final ProductoRepositoryPort productos;
    private final UsuarioRepositoryPort usuarios;
    private final Clock reloj;

    public GestionarPedidosService(PedidoRepositoryPort pedidos, ProductoRepositoryPort productos,
                                   UsuarioRepositoryPort usuarios, Clock reloj) {
        this.pedidos = pedidos;
        this.productos = productos;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Override
    public Pedido registrar(ComandoPedido comando, Long meseroId) {
        Usuario mesero = usuarios.buscarPorId(meseroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", meseroId));
        Pedido pedido = Pedido.registrar(comando.numeroMesa(), mesero, detalles(comando), comando.observaciones(),
                LocalDateTime.now(reloj));
        return pedidos.guardar(pedido);
    }

    @Override
    public Pedido modificar(Long pedidoId, ComandoPedido comando) {
        Pedido pedido = obtener(pedidoId);
        if (comando.numeroMesa() != pedido.getNumeroMesa()) {
            throw new DomainException("No se puede cambiar la mesa de un pedido; cancélelo y registre uno nuevo");
        }
        pedido.modificar(detalles(comando), comando.observaciones());
        return pedidos.guardar(pedido);
    }

    @Override
    public Pedido cancelar(Long pedidoId) {
        Pedido pedido = obtener(pedidoId);
        pedido.cancelar(LocalDateTime.now(reloj));
        return pedidos.guardar(pedido);
    }

    @Override
    public Pedido registrarVenta(Long pedidoId) {
        Pedido pedido = obtener(pedidoId);
        pedido.registrarVenta(LocalDateTime.now(reloj));
        // El descuento automático de inventario (RF-06) se agrega aquí en el sprint de inventario.
        return pedidos.guardar(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public Pedido obtener(Long pedidoId) {
        return pedidos.buscarPorId(pedidoId).orElseThrow(() -> new RecursoNoEncontradoException("Pedido", pedidoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> consultar(Integer numeroMesa, EstadoCocina estadoCocina, EstadoPedido estadoPedido) {
        return pedidos.buscar(numeroMesa, estadoCocina, estadoPedido);
    }

    /** Convierte cada línea pedida en un DetallePedido con el precio vigente del producto. */
    private List<DetallePedido> detalles(ComandoPedido comando) {
        if (comando.lineas() == null || comando.lineas().isEmpty()) {
            throw new DomainException("El pedido debe tener al menos un producto");
        }
        return comando.lineas().stream().map(linea -> {
            Producto producto = productos.buscarPorId(linea.productoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Producto", linea.productoId()));
            return DetallePedido.de(producto, linea.cantidad(), linea.nota());
        }).toList();
    }
}
