package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarHistorialUseCase.ResumenVentas;
import com.udec.restoflow.application.port.in.ConsultarPanelUseCase.ResumenPanel;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.ComandoPedido;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.LineaPedido;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.UnidadMedida;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** RF-08 y RF-12 · Panel del día e historial de ventas. */
class ConsultarPanelServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 25);   // el RelojManual arranca el 25/09 a las 10:00

    private Dobles.RelojManual reloj;
    private GestionarPedidosService ventas;
    private GestionarCocinaService cocina;
    private ConsultarPanelService panel;
    private ConsultarHistorialService historial;
    private Long meseroId;
    private Long bandejaId;
    private Long jugoId;

    @BeforeEach
    void preparar() {
        DoblesPedidos.PedidosEnMemoria pedidos = new DoblesPedidos.PedidosEnMemoria();
        DoblesPedidos.ProductosEnMemoria productos = new DoblesPedidos.ProductosEnMemoria();
        Dobles.UsuariosEnMemoria usuarios = new Dobles.UsuariosEnMemoria();
        DoblesInventario.IngredientesEnMemoria ingredientes = new DoblesInventario.IngredientesEnMemoria();
        DoblesInventario.AlertasEnMemoria alertas = new DoblesInventario.AlertasEnMemoria();
        reloj = new Dobles.RelojManual();
        ventas = new GestionarPedidosService(pedidos, productos, usuarios, p -> List.of(), reloj);
        cocina = new GestionarCocinaService(pedidos, reloj);
        panel = new ConsultarPanelService(pedidos, ingredientes, alertas);
        historial = new ConsultarHistorialService(pedidos);

        meseroId = usuarios.guardar(Usuario.nuevo("Laura", "laura@resto.com", "h", Rol.MESERO, LocalDateTime.now())).getId();
        bandejaId = productos.guardar(new Producto(null, "Bandeja paisa", null, new BigDecimal("32000"), 1L, "P", true)).getId();
        jugoId = productos.guardar(new Producto(null, "Jugo", null, new BigDecimal("7000"), 2L, "B", true)).getId();
        ingredientes.guardar(new Ingrediente(null, "Huevo", UnidadMedida.UNIDAD, new BigDecimal("4"), new BigDecimal("10"), true));
        ingredientes.guardar(new Ingrediente(null, "Arroz", UnidadMedida.GRAMO, new BigDecimal("9000"), new BigDecimal("10"), true));
    }

    private Pedido pedido(LineaPedido... lineas) {
        return ventas.registrar(new ComandoPedido(2, List.of(lineas), null), meseroId);
    }

    private Pedido vender(LineaPedido... lineas) {
        Pedido p = pedido(lineas);
        cocina.cambiarEstado(p.getId(), EstadoCocina.EN_PREPARACION);
        cocina.cambiarEstado(p.getId(), EstadoCocina.LISTO);
        cocina.cambiarEstado(p.getId(), EstadoCocina.ENTREGADO);
        return ventas.registrarVenta(p.getId());
    }

    @Test
    @DisplayName("Ventas del día, ticket promedio, ventas por hora, más vendidos e insumos en mínimo")
    void resumenDelDia() {
        vender(new LineaPedido(bandejaId, 1, null), new LineaPedido(jugoId, 2, null));   // 10:00 → 46.000
        reloj.avanzarMinutos(120);
        vender(new LineaPedido(jugoId, 1, null));                                        // 12:00 → 7.000
        pedido(new LineaPedido(bandejaId, 1, null));                                     // abierto, en cocina
        ventas.cancelar(pedido(new LineaPedido(jugoId, 1, null)).getId());               // cancelado

        ResumenPanel r = panel.resumen(HOY);

        assertEquals(0, new BigDecimal("53000").compareTo(r.ventas()));
        assertEquals(2, r.pedidosPagados());
        assertEquals(0, new BigDecimal("26500").compareTo(r.ticketPromedio()));
        assertEquals(1, r.pedidosAbiertos());
        assertEquals(1, r.pedidosEnCocina());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(List.of(10, 12), r.ventasPorHora().stream().map(v -> v.hora()).toList());
        assertEquals("Jugo", r.masVendidos().get(0).nombre());                           // 3 jugos > 1 bandeja
        assertEquals(3, r.masVendidos().get(0).cantidad());
        assertEquals(1, r.insumosEnMinimo().size());
        assertEquals("Huevo", r.insumosEnMinimo().get(0).nombre());
        assertEquals(0, BigDecimal.ZERO.compareTo(panel.resumen(HOY.plusDays(1)).ventas()));
        assertEquals(0, new BigDecimal("53000").compareTo(panel.resumen(HOY.plusDays(1)).ventasDiaAnterior()));
    }

    @Test
    @DisplayName("RF-12 · Historial de ventas por día y de pedidos por estado, con fechas válidas")
    void historialPorRango() {
        vender(new LineaPedido(bandejaId, 1, null));
        reloj.avanzarMinutos(24 * 60);
        vender(new LineaPedido(jugoId, 1, null));
        ventas.cancelar(pedido(new LineaPedido(jugoId, 1, null)).getId());

        ResumenVentas r = historial.ventas(HOY, HOY.plusDays(1));
        assertEquals(2, r.cantidad());
        assertEquals(0, new BigDecimal("39000").compareTo(r.total()));
        assertEquals(List.of(HOY, HOY.plusDays(1)), r.porDia().stream().map(d -> d.dia()).toList());
        assertEquals(1, historial.ventas(HOY, HOY).cantidad());

        assertEquals(3, historial.pedidos(HOY, HOY.plusDays(1), null).size());
        assertEquals(1, historial.pedidos(HOY, HOY.plusDays(1), EstadoPedido.CANCELADO).size());
        assertThrows(DomainException.class, () -> historial.pedidos(HOY, HOY.minusDays(1), null));
        assertThrows(DomainException.class, () -> historial.ventas(HOY, HOY.plusDays(200)));
        assertThrows(DomainException.class, () -> historial.ventas(null, HOY));
    }
}
