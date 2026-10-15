package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.out.AlertaRepositoryPort;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.MovimientoRepositoryPort;
import com.udec.restoflow.application.port.out.RecetaRepositoryPort;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.TipoMovimiento;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dobles en memoria para probar inventario, recetas y alertas sin base de datos. */
final class DoblesInventario {

    private DoblesInventario() {
    }

    static class IngredientesEnMemoria implements IngredienteRepositoryPort {
        final Map<Long, Ingrediente> datos = new LinkedHashMap<>();
        private long secuencia = 1;

        @Override
        public Ingrediente guardar(Ingrediente i) {
            Long id = i.getId() != null ? i.getId() : secuencia++;
            Ingrediente copia = new Ingrediente(id, i.getNombre(), i.getUnidad(), i.getStockActual(),
                    i.getStockMinimo(), i.isActivo());
            datos.put(id, copia);
            return copia;
        }

        @Override
        public Optional<Ingrediente> buscarPorId(Long id) {
            return Optional.ofNullable(datos.get(id)).map(this::copia);
        }

        @Override
        public List<Ingrediente> buscarPorIds(Collection<Long> ids) {
            return ids.stream().map(datos::get).filter(java.util.Objects::nonNull).map(this::copia).toList();
        }

        @Override
        public List<Ingrediente> listar(boolean soloActivos) {
            return datos.values().stream().filter(i -> !soloActivos || i.isActivo())
                    .sorted(Comparator.comparing(Ingrediente::getNombre)).map(this::copia).toList();
        }

        @Override
        public boolean existePorNombre(String nombre) {
            return datos.values().stream().anyMatch(i -> i.getNombre().equalsIgnoreCase(nombre));
        }

        @Override
        public long contar() {
            return datos.size();
        }

        /** Como una base de datos: quien lee recibe una copia, no el mismo objeto guardado. */
        private Ingrediente copia(Ingrediente i) {
            return new Ingrediente(i.getId(), i.getNombre(), i.getUnidad(), i.getStockActual(), i.getStockMinimo(),
                    i.isActivo());
        }
    }

    static class RecetasEnMemoria implements RecetaRepositoryPort {
        final List<RecetaIngrediente> datos = new ArrayList<>();
        private long secuencia = 1;

        @Override
        public List<RecetaIngrediente> deProducto(Long productoId) {
            return datos.stream().filter(r -> r.getProductoId().equals(productoId)).toList();
        }

        @Override
        public List<RecetaIngrediente> deProductos(Collection<Long> productoIds) {
            return datos.stream().filter(r -> productoIds.contains(r.getProductoId())).toList();
        }

        @Override
        public List<RecetaIngrediente> reemplazar(Long productoId, List<RecetaIngrediente> lineas) {
            datos.removeIf(r -> r.getProductoId().equals(productoId));
            lineas.forEach(l -> datos.add(new RecetaIngrediente(secuencia++, productoId, l.getIngredienteId(),
                    l.getCantidad())));
            return deProducto(productoId);
        }
    }

    static class MovimientosEnMemoria implements MovimientoRepositoryPort {
        final List<MovimientoInventario> datos = new ArrayList<>();

        @Override
        public MovimientoInventario guardar(MovimientoInventario m) {
            MovimientoInventario copia = new MovimientoInventario((long) datos.size() + 1, m.getIngredienteId(),
                    m.getTipo(), m.getCantidad(), m.getMotivo(), m.getUsuarioId(), m.getPedidoId(), m.getFecha());
            datos.add(copia);
            return copia;
        }

        @Override
        public List<MovimientoInventario> buscar(Long ingredienteId, TipoMovimiento tipo, LocalDateTime desde,
                                                 LocalDateTime hasta) {
            return datos.stream()
                    .filter(m -> ingredienteId == null || m.getIngredienteId().equals(ingredienteId))
                    .filter(m -> tipo == null || m.getTipo() == tipo)
                    .filter(m -> desde == null || !m.getFecha().isBefore(desde))
                    .filter(m -> hasta == null || m.getFecha().isBefore(hasta))
                    .sorted(Comparator.comparing(MovimientoInventario::getId).reversed())
                    .toList();
        }
    }

    static class AlertasEnMemoria implements AlertaRepositoryPort {
        final Map<Long, Alerta> datos = new LinkedHashMap<>();
        private long secuencia = 1;

        @Override
        public Alerta guardar(Alerta a) {
            Long id = a.getId() != null ? a.getId() : secuencia++;
            Alerta copia = new Alerta(id, a.getIngredienteId(), a.getMensaje(), a.getPrioridad(), a.isAtendida(),
                    a.getFechaCreacion(), a.getFechaAtencion());
            datos.put(id, copia);
            return copia;
        }

        @Override
        public Optional<Alerta> buscarPorId(Long id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public List<Alerta> listar(boolean soloPendientes) {
            return datos.values().stream().filter(a -> !soloPendientes || !a.isAtendida())
                    .sorted(Comparator.comparing(Alerta::getId).reversed()).toList();
        }

        @Override
        public List<Alerta> pendientesDe(Long ingredienteId) {
            return datos.values().stream()
                    .filter(a -> !a.isAtendida() && ingredienteId.equals(a.getIngredienteId())).toList();
        }

        @Override
        public long contarPendientes() {
            return datos.values().stream().filter(a -> !a.isAtendida()).count();
        }

        List<Alerta> pendientes() {
            return listar(true);
        }
    }
}
