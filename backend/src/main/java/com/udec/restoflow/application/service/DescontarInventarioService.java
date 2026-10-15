package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.DescontarInventarioUseCase;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.MovimientoRepositoryPort;
import com.udec.restoflow.application.port.out.RecetaRepositoryPort;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.TipoMovimiento;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF-06 · Al registrar una venta se descuentan los insumos según la receta de cada producto.
 * <ol>
 *   <li>Suma el consumo de cada insumo en todo el pedido (2 bandejas × 150 g de arroz = 300 g).</li>
 *   <li>Descuenta; si no alcanza, el stock queda en 0 y se crea una alerta ALTA (decisión del Sprint 4:
 *       la venta nunca se bloquea porque el plato ya se sirvió).</li>
 *   <li>Guarda una SALIDA por insumo con el número del pedido y revisa las alertas de bajo inventario.</li>
 * </ol>
 * Corre dentro de la transacción de la venta (RES-35): si algo falla, tampoco se registra la venta.
 */
@Transactional
public class DescontarInventarioService implements DescontarInventarioUseCase {

    private final RecetaRepositoryPort recetas;
    private final IngredienteRepositoryPort ingredientes;
    private final MovimientoRepositoryPort movimientos;
    private final ControlAlertas control;
    private final Clock reloj;

    public DescontarInventarioService(RecetaRepositoryPort recetas, IngredienteRepositoryPort ingredientes,
                                      MovimientoRepositoryPort movimientos, ControlAlertas control, Clock reloj) {
        this.recetas = recetas;
        this.ingredientes = ingredientes;
        this.movimientos = movimientos;
        this.control = control;
        this.reloj = reloj;
    }

    @Override
    public List<Consumo> descontarPorVenta(Pedido pedido) {
        Map<Long, Integer> unidadesPorProducto = pedido.getDetalles().stream()
                .collect(Collectors.groupingBy(DetallePedido::getProductoId, LinkedHashMap::new,
                        Collectors.summingInt(DetallePedido::getCantidad)));
        Map<Long, BigDecimal> consumoPorIngrediente = new LinkedHashMap<>();
        for (RecetaIngrediente linea : recetas.deProductos(unidadesPorProducto.keySet())) {
            BigDecimal consumo = linea.consumoPara(unidadesPorProducto.get(linea.getProductoId()));
            consumoPorIngrediente.merge(linea.getIngredienteId(), consumo, BigDecimal::add);
        }
        if (consumoPorIngrediente.isEmpty()) {
            return List.of();                    // ningún producto del pedido tiene receta
        }

        Map<Long, Ingrediente> porId = ingredientes.buscarPorIds(consumoPorIngrediente.keySet()).stream()
                .collect(Collectors.toMap(Ingrediente::getId, Function.identity()));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        String motivo = "Venta del pedido #" + pedido.getId() + " (mesa " + pedido.getNumeroMesa() + ")";
        List<Consumo> resultado = new ArrayList<>();

        consumoPorIngrediente.forEach((ingredienteId, cantidad) -> {
            Ingrediente ingrediente = porId.get(ingredienteId);
            BigDecimal faltante = ingrediente.consumir(cantidad);
            Ingrediente guardado = ingredientes.guardar(ingrediente);
            movimientos.guardar(MovimientoInventario.registrar(ingredienteId, TipoMovimiento.SALIDA, cantidad,
                    faltante.signum() > 0 ? motivo + " · faltaron " + ControlAlertas.numero(faltante) : motivo,
                    null, pedido.getId(), ahora));
            if (faltante.signum() > 0) {
                control.faltante(guardado, faltante, pedido.getId());
            }
            control.revisar(guardado);
            resultado.add(new Consumo(ingredienteId, guardado.getNombre(), cantidad, faltante));
        });
        return resultado;
    }
}
