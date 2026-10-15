package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Producto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Dobles en memoria para probar los casos de uso de pedidos y cocina sin base de datos. */
final class DoblesPedidos {

    private DoblesPedidos() {
    }

    static class ProductosEnMemoria implements ProductoRepositoryPort {
        final Map<Long, Producto> datos = new LinkedHashMap<>();
        private long secuencia = 1;

        @Override
        public Producto guardar(Producto p) {
            Long id = p.getId() != null ? p.getId() : secuencia++;
            Producto copia = new Producto(id, p.getNombre(), p.getDescripcion(), p.getPrecio(), p.getCategoriaId(),
                    p.getCategoriaNombre(), p.isActivo());
            datos.put(id, copia);
            return copia;
        }

        @Override
        public Optional<Producto> buscarPorId(Long id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public List<Producto> listar(boolean soloActivos, Long categoriaId) {
            return datos.values().stream()
                    .filter(p -> !soloActivos || p.isActivo())
                    .filter(p -> categoriaId == null || categoriaId.equals(p.getCategoriaId()))
                    .toList();
        }

        @Override
        public long contar() {
            return datos.size();
        }
    }

    /** Como la base de datos real: asigna id al pedido y a cada línea nueva. */
    static class PedidosEnMemoria implements PedidoRepositoryPort {
        final Map<Long, Pedido> datos = new LinkedHashMap<>();
        private long secuencia = 1;
        private final AtomicLong secuenciaLineas = new AtomicLong(1);

        @Override
        public Pedido guardar(Pedido p) {
            Long id = p.getId() != null ? p.getId() : secuencia++;
            List<DetallePedido> lineas = p.getDetalles().stream()
                    .map(d -> new DetallePedido(d.getId() != null ? d.getId() : secuenciaLineas.getAndIncrement(),
                            d.getProductoId(), d.getProductoNombre(), d.getCantidad(), d.getPrecioUnitario(), d.getNota()))
                    .toList();
            Pedido copia = new Pedido(id, p.getNumeroMesa(), p.getMeseroId(), p.getMeseroNombre(), lineas,
                    p.getObservaciones(), p.getEstadoCocina(), p.getEstadoPedido(), p.getFechaCreacion(),
                    p.getFechaInicioPreparacion(), p.getFechaListo(), p.getFechaEntrega(), p.getFechaCierre());
            datos.put(id, copia);
            return copia;
        }

        @Override
        public Optional<Pedido> buscarPorId(Long id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public List<Pedido> buscar(Integer numeroMesa, EstadoCocina estadoCocina, EstadoPedido estadoPedido) {
            return datos.values().stream()
                    .filter(p -> numeroMesa == null || p.getNumeroMesa() == numeroMesa)
                    .filter(p -> estadoCocina == null || p.getEstadoCocina() == estadoCocina)
                    .filter(p -> estadoPedido == null || p.getEstadoPedido() == estadoPedido)
                    .sorted(Comparator.comparing(Pedido::getFechaCreacion).reversed())
                    .toList();
        }

        @Override
        public List<Pedido> pendientesEnCocina() {
            return datos.values().stream()
                    .filter(Pedido::estaPendienteEnCocina)
                    .sorted(Comparator.comparing(Pedido::getFechaCreacion))
                    .toList();
        }

        @Override
        public List<Pedido> creadosEntre(LocalDateTime desde, LocalDateTime hasta, EstadoPedido estadoPedido) {
            return datos.values().stream()
                    .filter(p -> !p.getFechaCreacion().isBefore(desde) && p.getFechaCreacion().isBefore(hasta))
                    .filter(p -> estadoPedido == null || p.getEstadoPedido() == estadoPedido)
                    .sorted(Comparator.comparing(Pedido::getFechaCreacion).reversed())
                    .toList();
        }

        @Override
        public List<Pedido> pagadosEntre(LocalDateTime desde, LocalDateTime hasta) {
            return datos.values().stream()
                    .filter(p -> p.getEstadoPedido() == EstadoPedido.PAGADO)
                    .filter(p -> !p.getFechaCierre().isBefore(desde) && p.getFechaCierre().isBefore(hasta))
                    .sorted(Comparator.comparing(Pedido::getFechaCierre).reversed())
                    .toList();
        }
    }
}
