package com.udec.restoflow.domain;

import com.udec.restoflow.domain.ia.AnalisisVentas;
import com.udec.restoflow.domain.ia.AnalisisVentas.DemandaProducto;
import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.AnalisisVentas.ProductoTendencia;
import com.udec.restoflow.domain.ia.AnalizadorAbastecimiento;
import com.udec.restoflow.domain.ia.AnalizadorVentas;
import com.udec.restoflow.domain.ia.GeneradorRecomendaciones;
import com.udec.restoflow.domain.ia.PronosticoInsumo;
import com.udec.restoflow.domain.ia.Recomendacion;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RestoFlow AI (Sprint 5) · Reglas del motor de análisis, con datos de prueba inventados (RES-44).
 * Hoy es jueves 29 de octubre de 2026 (se analizan los días completos, hasta ayer); mañana es viernes.
 */
class RestoFlowAIDominioTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);
    /** El análisis usa días completos: los datos llegan hasta ayer. */
    private static final LocalDate AYER = HOY.minusDays(1);
    private static final long ARROZ = 1L;
    private static final long BANDEJA = 10L;
    private static final long JUGO = 20L;

    // ---------- Datos de prueba ----------

    private static Ingrediente arroz(String stock, String minimo) {
        return new Ingrediente(ARROZ, "Arroz", UnidadMedida.GRAMO, new BigDecimal(stock), new BigDecimal(minimo), true);
    }

    private static MovimientoInventario salida(long insumo, String cantidad, LocalDate dia) {
        return new MovimientoInventario(null, insumo, TipoMovimiento.SALIDA, new BigDecimal(cantidad), null, null, null,
                dia.atTime(13, 0));
    }

    /** Una salida de la cantidad indicada cada día, durante 7 días que terminan en el día dado. */
    private static List<MovimientoInventario> semanaDeSalidas(String porDia, LocalDate ultimoDia) {
        List<MovimientoInventario> lista = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            lista.add(salida(ARROZ, porDia, ultimoDia.minusDays(i)));
        }
        return lista;
    }

    private static Pedido venta(LocalDateTime cierre, DetallePedido... lineas) {
        return new Pedido(null, 1, 1L, "Laura", List.of(lineas), null, EstadoCocina.ENTREGADO, EstadoPedido.PAGADO,
                cierre.minusMinutes(40), null, null, null, cierre);
    }

    private static DetallePedido bandeja(int cantidad) {
        return new DetallePedido(null, BANDEJA, "Bandeja paisa", cantidad, new BigDecimal("32000"), null);
    }

    private static DetallePedido jugo(int cantidad) {
        return new DetallePedido(null, JUGO, "Jugo natural", cantidad, new BigDecimal("7000"), null);
    }

    /**
     * 4 semanas de ventas: entre semana 2 pedidos al día (bandeja + 2 jugos = 46.000);
     * los sábados 4 pedidos de bandeja (128.000).
     */
    private static List<Pedido> cuatroSemanas() {
        List<Pedido> ventas = new ArrayList<>();
        for (LocalDate d = HOY.minusDays(28); !d.isAfter(AYER); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY) {
                for (int i = 0; i < 4; i++) {
                    ventas.add(venta(d.atTime(LocalTime.of(13, 10)), bandeja(1)));
                }
            } else {
                ventas.add(venta(d.atTime(12, 50), bandeja(1)));
                ventas.add(venta(d.atTime(13, 20), jugo(2)));
            }
        }
        return ventas;
    }

    // ---------- RF-10 · Abastecimiento ----------

    @Test
    @DisplayName("RF-10 · Consumo diario, tendencia, días de cobertura y cantidad sugerida")
    void consumoTendenciaYCobertura() {
        List<MovimientoInventario> movimientos = new ArrayList<>(semanaDeSalidas("1000", AYER));      // 7.000 g esta semana
        movimientos.add(salida(ARROZ, "3500", HOY.minusDays(9)));                                     // 3.500 g la anterior

        PronosticoInsumo p = AnalizadorAbastecimiento.analizar(arroz("3000", "1000"), movimientos, HOY);

        assertEquals(0, new BigDecimal("1000").compareTo(p.consumoDiario()));
        assertEquals(0, new BigDecimal("7000").compareTo(p.consumoSemana()));
        assertEquals(100, p.tendenciaPorcentaje());                                   // se duplicó
        assertEquals(0, new BigDecimal("3").compareTo(p.diasCobertura()));            // 3.000 ÷ 1.000
        assertEquals(PrioridadAlerta.ALTA, p.prioridad());
        // 1.000 × 7 × (1 + 100 %) + 1.000 de mínimo − 3.000 que hay
        assertEquals(0, new BigDecimal("12000").compareTo(p.cantidadSugerida()));
    }

    @Test
    @DisplayName("RF-10 · Alcanza para 7 días o menos: prioridad MEDIA; sin semana anterior no hay tendencia")
    void prioridadMedia() {
        PronosticoInsumo p = AnalizadorAbastecimiento.analizar(arroz("5000", "1000"), semanaDeSalidas("1000", AYER), HOY);

        assertEquals(PrioridadAlerta.MEDIA, p.prioridad());
        assertNull(p.tendenciaPorcentaje());
        assertEquals(0, new BigDecimal("3000").compareTo(p.cantidadSugerida()));       // 7.000 + 1.000 − 5.000
    }

    @Test
    @DisplayName("RF-10 · Sin consumo no hay cobertura ni compra; en su mínimo se sugiere llegar al doble del mínimo")
    void sinConsumo() {
        PronosticoInsumo sobrado = AnalizadorAbastecimiento.analizar(arroz("5000", "1000"), List.of(), HOY);
        assertNull(sobrado.diasCobertura());
        assertNull(sobrado.prioridad());
        assertFalse(sobrado.requiereCompra());
        assertEquals(0, BigDecimal.ZERO.compareTo(sobrado.cantidadSugerida()));

        PronosticoInsumo bajo = AnalizadorAbastecimiento.analizar(arroz("800", "1000"), List.of(), HOY);
        assertEquals(PrioridadAlerta.ALTA, bajo.prioridad());
        assertEquals(0, new BigDecimal("1200").compareTo(bajo.cantidadSugerida()));    // 2 × 1.000 − 800
    }

    @Test
    @DisplayName("RF-10 · Solo cuentan las salidas de ese insumo en los días completos de las dos semanas")
    void soloSalidasDelInsumo() {
        List<MovimientoInventario> movimientos = List.of(
                salida(ARROZ, "700", AYER),
                salida(2L, "9999", HOY),                                                     // otro insumo
                new MovimientoInventario(null, ARROZ, TipoMovimiento.ENTRADA, new BigDecimal("5000"), null, null, null,
                        HOY.atTime(9, 0)),                                                   // una compra no es consumo
                salida(ARROZ, "9999", HOY.minusDays(20)),                                    // fuera de la ventana
                salida(ARROZ, "9999", HOY));                                                 // hoy aún no termina

        PronosticoInsumo p = AnalizadorAbastecimiento.analizar(arroz("10000", "1000"), movimientos, HOY);

        assertEquals(0, new BigDecimal("700").compareTo(p.consumoSemana()));
        assertEquals(0, new BigDecimal("100").compareTo(p.consumoDiario()));
        assertNull(p.tendenciaPorcentaje());
    }

    // ---------- RF-09 · Ventas y predicción ----------

    @Test
    @DisplayName("RF-09 · Días con ventas, semana, día más fuerte, hora pico y ventas por día de la semana")
    void analisisDeVentas() {
        AnalisisVentas a = AnalizadorVentas.analizar(cuatroSemanas(), HOY);

        assertEquals(28, a.diasConVentas());
        assertTrue(a.datosSuficientes());
        assertEquals(0, new BigDecimal("404000").compareTo(a.ventasSemana()));        // 6 × 46.000 + 128.000
        assertEquals(0, a.tendenciaVentas());                                          // igual que la semana anterior
        assertEquals(DayOfWeek.SATURDAY, a.diaMasFuerte());
        assertEquals(12, a.horaPico());                                               // los pedidos se hacen a las 12
        assertEquals(7, a.ventasPorDia().size());
        assertEquals(DayOfWeek.MONDAY, a.ventasPorDia().get(0).dia());
        assertEquals(0, new BigDecimal("128000").compareTo(a.ventasPorDia().get(5).promedio()));
    }

    @Test
    @DisplayName("RES-41 · Predicción de mañana (viernes): promedio de los últimos viernes")
    void prediccionDeManana() {
        Prediccion p = AnalizadorVentas.analizar(cuatroSemanas(), HOY).prediccion();

        assertNotNull(p);
        assertEquals(HOY.plusDays(1), p.fecha());
        assertEquals(4, p.basadoEnDias());                                             // 4 viernes en 4 semanas
        assertEquals(0, new BigDecimal("46000").compareTo(p.ventasEsperadas()));
        assertEquals(2, p.pedidosEsperados());
        assertEquals(List.of(new DemandaProducto(JUGO, "Jugo natural", 2), new DemandaProducto(BANDEJA, "Bandeja paisa", 1)),
                p.productos());
        assertEquals(-20, p.variacionPromedio());                                      // un viernes vende menos que el promedio
    }

    @Test
    @DisplayName("RES-38 · Tendencia de cada producto frente a la semana anterior")
    void tendenciaDeProductos() {
        List<Pedido> ventas = List.of(
                venta(AYER.atTime(13, 0), jugo(10)),
                venta(HOY.minusDays(8).atTime(13, 0), jugo(4)),
                venta(HOY.minusDays(8).atTime(13, 0), bandeja(6)));

        AnalisisVentas a = AnalizadorVentas.analizar(ventas, HOY);

        ProductoTendencia jugo = a.productos().get(0);
        assertEquals("Jugo natural", jugo.nombre());
        assertEquals(150, jugo.tendenciaPorcentaje());                                 // de 4 a 10
        ProductoTendencia bandeja = a.productos().get(1);
        assertEquals(0, bandeja.unidadesSemana());
        assertEquals(-100, bandeja.tendenciaPorcentaje());
        assertFalse(a.datosSuficientes());
    }

    @Test
    @DisplayName("RF-09 · Sin ventas no hay predicción ni día fuerte")
    void sinVentas() {
        AnalisisVentas a = AnalizadorVentas.analizar(List.of(), HOY);

        assertEquals(0, a.diasConVentas());
        assertNull(a.prediccion());
        assertNull(a.diaMasFuerte());
        assertNull(a.horaPico());
        assertNull(a.tendenciaVentas());
    }

    // ---------- RF-11 · Recomendaciones ----------

    @Test
    @DisplayName("RF-11 · Recomendación de compra con cantidad, días que alcanza y tendencia")
    void recomendacionDeCompra() {
        List<MovimientoInventario> movimientos = new ArrayList<>(semanaDeSalidas("1000", AYER));
        movimientos.add(salida(ARROZ, "3500", HOY.minusDays(9)));
        PronosticoInsumo arroz = AnalizadorAbastecimiento.analizar(arroz("3000", "1000"), movimientos, HOY);

        List<Recomendacion> r = GeneradorRecomendaciones.generar(List.of(arroz), null);

        assertEquals(1, r.size());
        assertEquals(Recomendacion.Tipo.COMPRA, r.get(0).tipo());
        assertEquals(PrioridadAlerta.ALTA, r.get(0).prioridad());
        assertEquals("Comprar 12.000 g de Arroz", r.get(0).titulo());
        assertEquals("Alcanza para 3 días al ritmo actual (1.000 g al día). El consumo subió 100 % esta semana.",
                r.get(0).detalle());
        assertEquals(ARROZ, r.get(0).ingredienteId());
    }

    @Test
    @DisplayName("RF-11 · Mañana se espera más demanda: revisar el inventario antes")
    void recomendacionDeDemanda() {
        Prediccion manana = new Prediccion(HOY.plusDays(1), new BigDecimal("135000"), 6, 4, 35,
                List.of(new DemandaProducto(BANDEJA, "Bandeja paisa", 4)));
        AnalisisVentas ventas = new AnalisisVentas(20, true, new BigDecimal("600000"), 5, DayOfWeek.SATURDAY, 13,
                List.of(), List.of(), manana);

        List<Recomendacion> r = GeneradorRecomendaciones.generar(List.of(), ventas);

        assertEquals(1, r.size());
        assertEquals(Recomendacion.Tipo.DEMANDA, r.get(0).tipo());
        assertEquals(PrioridadAlerta.MEDIA, r.get(0).prioridad());
        assertEquals("Mañana viernes se espera más demanda", r.get(0).titulo());
        assertTrue(r.get(0).detalle().startsWith("Ventas esperadas: $ 135.000, 35 % más que un día promedio."));
        assertTrue(r.get(0).detalle().contains("Bandeja paisa (4)"));
    }

    @Test
    @DisplayName("RF-11 · Productos que bajan o suben, aviso de pocos datos y orden por prioridad")
    void productosDatosYOrden() {
        AnalisisVentas ventas = new AnalisisVentas(3, false, new BigDecimal("100000"), null, null, 12,
                List.of(new ProductoTendencia(JUGO, "Jugo natural", 12, 5, 140),
                        new ProductoTendencia(BANDEJA, "Bandeja paisa", 2, 10, -80),
                        new ProductoTendencia(30L, "Gaseosa", 1, 2, -50)),          // muy pocas unidades: se ignora
                List.of(), null);
        PronosticoInsumo bajo = AnalizadorAbastecimiento.analizar(arroz("800", "1000"), List.of(), HOY);

        List<Recomendacion> r = GeneradorRecomendaciones.generar(List.of(bajo), ventas);

        assertEquals(4, r.size());
        assertEquals(Recomendacion.Tipo.COMPRA, r.get(0).tipo());                    // ALTA primero
        assertEquals("Crecen las ventas de Jugo natural", r.get(1).titulo());
        assertEquals("Bajan las ventas de Bandeja paisa", r.get(2).titulo());
        assertTrue(r.get(2).detalle().startsWith("Se vendieron 2 esta semana frente a 10 la anterior (-80 %)."));
        assertEquals(Recomendacion.Tipo.INFORMACION, r.get(3).tipo());
        assertTrue(r.get(3).detalle().startsWith("Hay 3 días con ventas"));
    }

    @Test
    @DisplayName("RES-38 · Un insumo que no necesita compra pero sube 20 % o más genera un aviso de tendencia")
    void tendenciaDeInsumo() {
        List<MovimientoInventario> movimientos = new ArrayList<>(semanaDeSalidas("100", AYER));         // 700 g
        movimientos.add(salida(ARROZ, "500", HOY.minusDays(10)));                                     // 500 g antes

        PronosticoInsumo p = AnalizadorAbastecimiento.analizar(arroz("50000", "1000"), movimientos, HOY);
        List<Recomendacion> r = GeneradorRecomendaciones.generar(List.of(p), null);

        assertEquals(40, p.tendenciaPorcentaje());
        assertEquals(1, r.size());
        assertEquals(Recomendacion.Tipo.TENDENCIA, r.get(0).tipo());
        assertEquals("Sube el consumo de Arroz", r.get(0).titulo());
    }
}
