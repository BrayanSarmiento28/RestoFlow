package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarInventarioUseCase.ComandoIngrediente;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF-05, RES-32 y RF-07 · Insumos, movimientos manuales y alertas automáticas. */
class GestionarInventarioServiceTest {

    private DoblesInventario.IngredientesEnMemoria ingredientes;
    private DoblesInventario.MovimientosEnMemoria movimientos;
    private DoblesInventario.AlertasEnMemoria alertas;
    private GestionarInventarioService servicio;
    private static final Long USUARIO = 7L;

    @BeforeEach
    void preparar() {
        ingredientes = new DoblesInventario.IngredientesEnMemoria();
        movimientos = new DoblesInventario.MovimientosEnMemoria();
        alertas = new DoblesInventario.AlertasEnMemoria();
        Dobles.RelojManual reloj = new Dobles.RelojManual();
        servicio = new GestionarInventarioService(ingredientes, movimientos, new ControlAlertas(alertas, reloj), reloj);
    }

    private Ingrediente arroz() {
        return servicio.crear(new ComandoIngrediente("Arroz", UnidadMedida.GRAMO, new BigDecimal("1000"),
                new BigDecimal("5000")), USUARIO);
    }

    private static boolean igual(String esperado, BigDecimal real) {
        return new BigDecimal(esperado).compareTo(real) == 0;
    }

    @Test
    @DisplayName("Crear con stock inicial lo registra como entrada; el nombre no se puede repetir")
    void crearConStockInicial() {
        Ingrediente arroz = arroz();

        assertTrue(igual("5000", arroz.getStockActual()));
        assertEquals(1, movimientos.datos.size());
        MovimientoInventario entrada = movimientos.datos.get(0);
        assertEquals(TipoMovimiento.ENTRADA, entrada.getTipo());
        assertEquals("Stock inicial", entrada.getMotivo());
        assertEquals(USUARIO, entrada.getUsuarioId());
        assertThrows(OperacionNoPermitidaException.class, () -> servicio.crear(
                new ComandoIngrediente("arroz", UnidadMedida.GRAMO, BigDecimal.ONE, null), USUARIO));
    }

    @Test
    @DisplayName("Entradas suman, salidas restan (con motivo obligatorio) y nunca se saca más de lo que hay")
    void entradasYSalidas() {
        Long id = arroz().getId();

        servicio.registrarEntrada(id, new BigDecimal("2000"), "Compra Makro", USUARIO);
        Ingrediente despues = servicio.registrarSalida(id, new BigDecimal("500"), "Se mojó un bulto", USUARIO);

        assertTrue(igual("6500", despues.getStockActual()));
        assertThrows(DomainException.class, () -> servicio.registrarSalida(id, BigDecimal.ONE, " ", USUARIO));
        assertThrows(OperacionNoPermitidaException.class,
                () -> servicio.registrarSalida(id, new BigDecimal("9999"), "Merma", USUARIO));
        assertTrue(igual("6500", servicio.obtener(id).getStockActual()));
        assertEquals(3, movimientos.datos.size());
    }

    @Test
    @DisplayName("El ajuste deja el stock en lo contado y guarda cuánto había registrado")
    void ajustePorConteo() {
        Long id = arroz().getId();

        Ingrediente ajustado = servicio.ajustar(id, new BigDecimal("4200"), "Conteo semanal", USUARIO);

        assertTrue(igual("4200", ajustado.getStockActual()));
        MovimientoInventario ajuste = movimientos.datos.get(1);
        assertEquals(TipoMovimiento.AJUSTE, ajuste.getTipo());
        assertTrue(igual("4200", ajuste.getCantidad()));
        assertEquals("Conteo físico: había 5000 registrado. Conteo semanal", ajuste.getMotivo());
    }

    @Test
    @DisplayName("RF-07 · Al llegar al mínimo se crea UNA alerta media; al agotarse pasa a alta; al reabastecer se cierra sola")
    void ciclo_de_alertas() {
        Long id = arroz().getId();
        assertTrue(alertas.pendientes().isEmpty());

        servicio.registrarSalida(id, new BigDecimal("4100"), "Merma", USUARIO);      // quedan 900 (mínimo 1000)
        servicio.registrarSalida(id, new BigDecimal("100"), "Merma", USUARIO);       // 800: no repite la alerta
        List<Alerta> pendientes = alertas.pendientes();
        assertEquals(1, pendientes.size());
        assertEquals(PrioridadAlerta.MEDIA, pendientes.get(0).getPrioridad());
        assertEquals("Quedan 800 gramo de Arroz (mínimo 1000).".replace("800", "900"), pendientes.get(0).getMensaje());

        servicio.ajustar(id, BigDecimal.ZERO, null, USUARIO);                       // se agotó
        pendientes = alertas.pendientes();
        assertEquals(1, pendientes.size());
        assertEquals(PrioridadAlerta.ALTA, pendientes.get(0).getPrioridad());

        servicio.registrarEntrada(id, new BigDecimal("3000"), "Compra", USUARIO);  // vuelve por encima del mínimo
        assertTrue(alertas.pendientes().isEmpty());
        assertEquals(2, alertas.datos.size());
        assertTrue(alertas.datos.values().stream().allMatch(Alerta::isAtendida));
    }

    @Test
    @DisplayName("Subir el stock mínimo por encima del actual también genera la alerta")
    void cambiarMinimoAbreAlerta() {
        Long id = arroz().getId();
        servicio.actualizar(id, new ComandoIngrediente("Arroz", UnidadMedida.GRAMO, new BigDecimal("6000"), null));
        assertEquals(1, alertas.pendientes().size());
    }

    @Test
    @DisplayName("Desactivar no borra el insumo; el historial se filtra por insumo, tipo y fechas")
    void desactivarYFiltrarHistorial() {
        Long id = arroz().getId();
        servicio.registrarEntrada(id, new BigDecimal("100"), null, USUARIO);

        Ingrediente inactivo = servicio.cambiarEstado(id, false);

        assertFalse(inactivo.isActivo());
        assertEquals(1, servicio.listar(false).size());
        assertTrue(servicio.listar(true).isEmpty());
        assertEquals(2, servicio.movimientos(id, TipoMovimiento.ENTRADA, null, null).size());
        assertNull(servicio.movimientos(id, null, null, null).get(0).getMotivo());
        LocalDate dia = LocalDate.of(2026, 9, 25);
        assertEquals(2, servicio.movimientos(null, null, dia, dia).size());
        assertTrue(servicio.movimientos(null, null, dia.plusDays(1), dia.plusDays(2)).isEmpty());
        assertThrows(DomainException.class, () -> servicio.movimientos(null, null, dia, dia.minusDays(1)));
    }
}
