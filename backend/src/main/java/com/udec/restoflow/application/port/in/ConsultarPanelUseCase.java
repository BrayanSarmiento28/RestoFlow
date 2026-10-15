package com.udec.restoflow.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** RF-08 · Resumen del día para el panel del administrador: ventas, pedidos e inventario. */
public interface ConsultarPanelUseCase {

    ResumenPanel resumen(LocalDate dia);

    record ResumenPanel(LocalDate dia, BigDecimal ventas, int pedidosPagados, BigDecimal ticketPromedio,
                        BigDecimal ventasDiaAnterior, int pedidosAbiertos, int pedidosEnCocina,
                        int pedidosCancelados, List<VentaPorHora> ventasPorHora, List<ProductoVendido> masVendidos,
                        List<InsumoBajo> insumosEnMinimo, int alertasPendientes) { }

    record VentaPorHora(int hora, BigDecimal total, int pedidos) { }

    record ProductoVendido(Long productoId, String nombre, int cantidad, BigDecimal total) { }

    record InsumoBajo(Long id, String nombre, BigDecimal stockActual, BigDecimal stockMinimo, String unidad) { }
}
