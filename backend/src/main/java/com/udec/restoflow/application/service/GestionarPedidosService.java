package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.DescontarInventarioUseCase;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.MesaOcupadaException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.MetodoPago;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.Usuario;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
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
    private final DescontarInventarioUseCase inventario;
    private final Clock reloj;

    public GestionarPedidosService(PedidoRepositoryPort pedidos, ProductoRepositoryPort productos,
                                   UsuarioRepositoryPort usuarios, DescontarInventarioUseCase inventario, Clock reloj) {
        this.pedidos = pedidos;
        this.productos = productos;
        this.usuarios = usuarios;
        this.inventario = inventario;
        this.reloj = reloj;
    }

    @Override
    public Pedido registrar(ComandoPedido comando, Long meseroId) {
        Usuario mesero = usuarios.buscarPorId(meseroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", meseroId));
        validarMesaLibre(comando.numeroMesa(), null);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Pedido pedido = Pedido.registrar(comando.numeroMesa(), mesero, detalles(comando), comando.observaciones(), ahora);
        // RN: la numeración de los pedidos vuelve a empezar cada día (#1 es el primero del día)
        pedido.asignarNumeroDelDia(pedidos.ultimoNumeroDelDia(ahora.toLocalDate()) + 1);
        Pedido guardado = pedidos.guardar(pedido);
        Auditoria.pedidoRegistrado(guardado.getId(), guardado.getNumeroMesa(), meseroId, guardado.getTotal());
        return guardado;
    }

    @Override
    public Pedido modificar(Long pedidoId, ComandoPedido comando) {
        Pedido pedido = obtener(pedidoId);
        if (comando.numeroMesa() != pedido.getNumeroMesa()) {
            throw new DomainException("La mesa no se cambia al modificar el pedido; use la opción \"Cambiar mesa\"");
        }
        pedido.modificar(detalles(comando), comando.observaciones());
        return pedidos.guardar(pedido);
    }

    @Override
    public Pedido cancelar(Long pedidoId) {
        Pedido pedido = obtener(pedidoId);
        pedido.cancelar(LocalDateTime.now(reloj));
        Pedido cancelado = pedidos.guardar(pedido);
        Auditoria.pedidoCancelado(cancelado.getId());
        return cancelado;
    }

    @Override
    public Pedido registrarVenta(Long pedidoId, MetodoPago metodo) {
        Pedido pedido = obtener(pedidoId);
        pedido.registrarVenta(LocalDateTime.now(reloj), metodo);
        Pedido vendido = pedidos.guardar(pedido);
        // RF-06 · Descuento automático según las recetas, en la misma transacción (RES-35)
        inventario.descontarPorVenta(vendido);
        Auditoria.ventaRegistrada(vendido.getId(), vendido.getNumeroMesa(), vendido.getTotal(), metodo);
        return vendido;
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

    @Override
    public Pedido agregarAdicion(Long pedidoId, List<LineaPedido> lineas) {
        Pedido pedido = obtener(pedidoId);
        if (lineas == null || lineas.isEmpty()) {
            throw new DomainException("Agregue al menos un producto a la adición");
        }
        List<DetallePedido> adicion = lineas.stream()
                .map(linea -> DetallePedido.adicionDe(producto(linea.productoId()), linea.cantidad(), linea.nota()))
                .toList();
        BigDecimal valor = adicion.stream().map(DetallePedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        pedido.agregarAdicion(adicion, LocalDateTime.now(reloj));
        Pedido guardado = pedidos.guardar(pedido);
        Auditoria.adicionAgregada(guardado.getId(), guardado.getNumeroMesa(), adicion.size(), valor);
        return guardado;
    }

    @Override
    public Pedido cambiarMesa(Long pedidoId, int nuevaMesa) {
        Pedido pedido = obtener(pedidoId);
        int anterior = pedido.getNumeroMesa();
        validarMesaLibre(nuevaMesa, pedidoId);
        pedido.cambiarMesa(nuevaMesa);
        Pedido guardado = pedidos.guardar(pedido);
        Auditoria.mesaCambiada(guardado.getId(), anterior, nuevaMesa);
        return guardado;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MesaOcupada> mesasOcupadas() {
        return pedidos.buscar(null, null, EstadoPedido.ABIERTO).stream()
                .map(p -> new MesaOcupada(p.getNumeroMesa(), p.getId(), p.getMeseroNombre(), p.getEstadoCocina()))
                .sorted(Comparator.comparingInt(MesaOcupada::numeroMesa))
                .toList();
    }

    /** RN: una mesa con un pedido abierto está ocupada (sin contar el propio pedido cuando se cambia de mesa). */
    private void validarMesaLibre(int mesa, Long excepto) {
        pedidos.buscar(mesa, null, EstadoPedido.ABIERTO).stream()
                .filter(p -> p.ocupaMesa() && !p.getId().equals(excepto))
                .findFirst()
                .ifPresent(p -> {
                    throw new MesaOcupadaException(mesa, p.getId(), p.getMeseroNombre());
                });
    }

    /** Convierte cada línea pedida en un DetallePedido con el precio vigente del producto. */
    private List<DetallePedido> detalles(ComandoPedido comando) {
        if (comando.lineas() == null || comando.lineas().isEmpty()) {
            throw new DomainException("El pedido debe tener al menos un producto");
        }
        return comando.lineas().stream()
                .map(linea -> DetallePedido.de(producto(linea.productoId()), linea.cantidad(), linea.nota()))
                .toList();
    }

    private Producto producto(Long id) {
        return productos.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Producto", id));
    }
}
