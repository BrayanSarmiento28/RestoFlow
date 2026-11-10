package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.Pedido;

import java.math.BigDecimal;
import java.util.List;

/**
 * RF-06 · Descuenta del inventario los insumos de un pedido vendido, según las recetas.
 * Se llama dentro de la misma transacción que registra la venta (RES-35): o se guarda todo o nada.
 */
public interface DescontarInventarioUseCase {

    List<Consumo> descontarPorVenta(Pedido pedido);

    /** Cuánto se descontó de cada insumo y cuánto faltó si no alcanzaba. */
    record Consumo(Long ingredienteId, String ingrediente, BigDecimal cantidad, BigDecimal faltante) { }
}
