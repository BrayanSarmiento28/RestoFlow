package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.DescontarInventarioUseCase.Consumo;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.ComandoPedido;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.LineaPedido;
import com.udec.restoflow.application.port.in.GestionarRecetasUseCase.LineaReceta;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.domain.model.UnidadMedida;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF-06 y RES-35 · Al registrar la venta se descuentan los insumos según las recetas. */
class DescontarInventarioServiceTest {

    private DoblesInventario.IngredientesEnMemoria ingredientes;
    private DoblesInventario.MovimientosEnMemoria movimientos;
    private DoblesInventario.AlertasEnMemoria alertas;
    private DoblesPedidos.PedidosEnMemoria pedidos;
    private GestionarRecetasService recetas;
    private GestionarPedidosService ventas;
    private GestionarCocinaService cocina;
    private Long meseroId;
    private Long bandejaId;
    private Long ajiacoId;
    private Long gaseosaId;
    private Long arrozId;
    private Long huevoId;

    @BeforeEach
    void preparar() {
        ingredientes = new DoblesInventario.IngredientesEnMemoria();
        movimientos = new DoblesInventario.MovimientosEnMemoria();
        alertas = new DoblesInventario.AlertasEnMemoria();
        pedidos = new DoblesPedidos.PedidosEnMemoria();
        DoblesPedidos.ProductosEnMemoria productos = new DoblesPedidos.ProductosEnMemoria();
        Dobles.UsuariosEnMemoria usuarios = new Dobles.UsuariosEnMemoria();
        Dobles.RelojManual reloj = new Dobles.RelojManual();
        DoblesInventario.RecetasEnMemoria recetasRepo = new DoblesInventario.RecetasEnMemoria();
        ControlAlertas control = new ControlAlertas(alertas, reloj);

        recetas = new GestionarRecetasService(recetasRepo, productos, ingredientes);
        DescontarInventarioService descuento =
                new DescontarInventarioService(recetasRepo, ingredientes, movimientos, control, reloj);
        ventas = new GestionarPedidosService(pedidos, productos, usuarios, descuento, reloj);
        cocina = new GestionarCocinaService(pedidos, reloj);

        meseroId = usuarios.guardar(Usuario.nuevo("Laura Gómez", "laura@resto.com", "hash", Rol.MESERO,
                LocalDateTime.now())).getId();
        bandejaId = productos.guardar(new Producto(null, "Bandeja paisa", null, new BigDecimal("32000"), 1L,
                "Platos fuertes", true)).getId();
        ajiacoId = productos.guardar(new Producto(null, "Ajiaco", null, new BigDecimal("28000"), 1L,
                "Platos fuertes", true)).getId();
        gaseosaId = productos.guardar(new Producto(null, "Gaseosa", null, new BigDecimal("5000"), 2L,
                "Bebidas", true)).getId();
        arrozId = ingredientes.guardar(new Ingrediente(null, "Arroz", UnidadMedida.GRAMO, new BigDecimal("1000"),
                new BigDecimal("300"), true)).getId();
        huevoId = ingredientes.guardar(new Ingrediente(null, "Huevo", UnidadMedida.UNIDAD, new BigDecimal("3"),
                new BigDecimal("1"), true)).getId();

        recetas.definir(bandejaId, List.of(new LineaReceta(arrozId, new BigDecimal("150")),
                new LineaReceta(huevoId, BigDecimal.ONE)));
        recetas.definir(ajiacoId, List.of(new LineaReceta(arrozId, new BigDecimal("80"))));
    }

    /** Registra el pedido, lo pasa por cocina hasta Entregado y registra la venta. */
    private Pedido vender(LineaPedido... lineas) {
        Pedido pedido = ventas.registrar(new ComandoPedido(4, List.of(lineas), null), meseroId);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.EN_PREPARACION);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.LISTO);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.ENTREGADO);
        return ventas.registrarVenta(pedido.getId());
    }

    private BigDecimal stock(Long id) {
        return ingredientes.buscarPorId(id).orElseThrow().getStockActual();
    }

    @Test
    @DisplayName("Suma el consumo de todo el pedido: 2 bandejas + 1 ajiaco = 380 g de arroz y 2 huevos")
    void descuentaSegunRecetas() {
        Pedido venta = vender(new LineaPedido(bandejaId, 2, null), new LineaPedido(ajiacoId, 1, null),
                new LineaPedido(gaseosaId, 3, null));          // la gaseosa no tiene receta: no descuenta nada

        assertEquals(EstadoPedido.PAGADO, venta.getEstadoPedido());
        assertEquals(0, new BigDecimal("620").compareTo(stock(arrozId)));
        assertEquals(0, BigDecimal.ONE.compareTo(stock(huevoId)));

        assertEquals(2, movimientos.datos.size());               // una salida por insumo, no por línea
        MovimientoInventario arroz = movimientos.datos.get(0);
        assertEquals(TipoMovimiento.SALIDA, arroz.getTipo());
        assertEquals(0, new BigDecimal("380").compareTo(arroz.getCantidad()));
        assertEquals(venta.getId(), arroz.getPedidoId());
        assertEquals(null, arroz.getUsuarioId());                // lo hizo el sistema
        assertEquals("Venta del pedido #" + venta.getId() + " (mesa 4)", arroz.getMotivo());
    }

    @Test
    @DisplayName("Si no alcanza, la venta igual se registra, el insumo queda en 0 y se crea una alerta ALTA")
    void sinStockSuficiente() {
        Pedido venta = vender(new LineaPedido(bandejaId, 5, null));   // pide 5 huevos y solo hay 3

        assertEquals(EstadoPedido.PAGADO, venta.getEstadoPedido());
        assertEquals(0, BigDecimal.ZERO.compareTo(stock(huevoId)));
        assertTrue(movimientos.datos.stream().anyMatch(m -> m.getMotivo().endsWith("faltaron 2")));

        List<Alerta> deHuevo = alertas.pendientes().stream().filter(a -> a.getIngredienteId().equals(huevoId)).toList();
        assertEquals(1, deHuevo.size());                          // una sola alerta ALTA: no se repite "se agotó"
        assertEquals(PrioridadAlerta.ALTA, deHuevo.get(0).getPrioridad());
        assertTrue(deHuevo.get(0).getMensaje().startsWith("La venta del pedido #" + venta.getId()
                + " necesitaba 2 unidad más de Huevo"));
        // El arroz bajó de 1000 a 250: queda por debajo de su mínimo (300) → alerta media
        assertTrue(alertas.pendientes().stream().anyMatch(a -> a.getIngredienteId().equals(arrozId)
                && a.getPrioridad() == PrioridadAlerta.MEDIA));
    }

    @Test
    @DisplayName("La venta de un pedido sin productos con receta no toca el inventario")
    void sinReceta() {
        vender(new LineaPedido(gaseosaId, 2, null));
        assertTrue(movimientos.datos.isEmpty());
        assertEquals(0, new BigDecimal("1000").compareTo(stock(arrozId)));
    }

    @Test
    @DisplayName("La receta valida producto, ingrediente, repetidos, y se puede vaciar")
    void reglasDeLaReceta() {
        assertThrows(RecursoNoEncontradoException.class, () -> recetas.receta(999L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> recetas.definir(bandejaId, List.of(new LineaReceta(999L, BigDecimal.ONE))));
        assertThrows(DomainException.class, () -> recetas.definir(bandejaId,
                List.of(new LineaReceta(arrozId, BigDecimal.ONE), new LineaReceta(arrozId, BigDecimal.TEN))));
        assertThrows(DomainException.class,
                () -> recetas.definir(bandejaId, List.of(new LineaReceta(arrozId, BigDecimal.ZERO))));
        assertEquals(2, recetas.receta(bandejaId).size());        // las fallidas no cambiaron nada

        assertTrue(recetas.definir(bandejaId, List.of()).isEmpty());
        List<Consumo> nada = new DescontarInventarioService(new DoblesInventario.RecetasEnMemoria(), ingredientes,
                movimientos, new ControlAlertas(alertas, new Dobles.RelojManual()), new Dobles.RelojManual())
                .descontarPorVenta(vender(new LineaPedido(bandejaId, 1, null)));
        assertTrue(nada.isEmpty());
    }
}
