package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.ia.AnalisisVentas.DemandaProducto;
import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.AnalisisVentas.ProductoTendencia;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos límite de las reglas de RestoFlow AI (recomendaciones, avisos y datos para Gemini) que no cubren las pruebas
 * de los escenarios principales.
 */
class ReglasRestoFlowAITest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 12, 15, 0);
    private static final LocalDate MANANA = LocalDate.of(2026, 11, 13);

    private static PronosticoInsumo insumo(String stock, String dias, PrioridadAlerta prioridad, String sugerida,
                                           Integer tendencia, String consumoSemana) {
        return new PronosticoInsumo(1L, "Papa", UnidadMedida.GRAMO, new BigDecimal(stock), new BigDecimal("1000"),
                new BigDecimal("200"), new BigDecimal(consumoSemana), tendencia,
                dias == null ? null : new BigDecimal(dias), prioridad, sugerida == null ? null : new BigDecimal(sugerida));
    }

    private static AnalisisVentas ventas(boolean suficientes, Prediccion prediccion, List<ProductoTendencia> productos) {
        return new AnalisisVentas(20, suficientes, new BigDecimal("600000"), 5, DayOfWeek.SATURDAY, 13,
                productos, List.of(), prediccion);
    }

    private static Pedido pedido(EstadoCocina estado, LocalDateTime listo, LocalDateTime entrega) {
        DetallePedido ajiaco = new DetallePedido(1L, 4L, "Ajiaco", 1, new BigDecimal("28000"), null);
        return new Pedido(9L, 6, 7L, "Laura", List.of(ajiaco), null, estado, EstadoPedido.ABIERTO,
                AHORA.minusMinutes(90), AHORA.minusMinutes(80), listo, entrega, null);
    }

    // ---------- Recomendaciones ----------

    @Test
    @DisplayName("Compra: un insumo agotado dice \"Se agotó\"; uno en su mínimo sin consumo lo dice así")
    void detalleDeCompra() {
        List<Recomendacion> r = GeneradorRecomendaciones.generar(List.of(
                insumo("0", "0", PrioridadAlerta.ALTA, "6000", null, "1400"),
                insumo("900", null, PrioridadAlerta.MEDIA, "5100", 10, "0")), null);

        assertThat(r).hasSize(2);
        assertThat(r.get(0).detalle()).isEqualTo("Se agotó.");
        assertThat(r.get(1).detalle()).isEqualTo("Está en su mínimo (1.000 g).");
    }

    @Test
    @DisplayName("Sin cantidad sugerida no hay compra; sin consumo esta semana no hay aviso de tendencia")
    void sinRecomendacion() {
        List<Recomendacion> r = GeneradorRecomendaciones.generar(List.of(
                insumo("900", "4", PrioridadAlerta.BAJA, "0", 50, "700"),
                insumo("9000", "40", null, null, 50, "0"),
                insumo("9000", "40", null, null, null, "700")), null);

        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("Demanda: solo con datos suficientes y un aumento del 20 % o más; cuenta los insumos por comprar")
    void demanda() {
        Prediccion fuerte = new Prediccion(MANANA, new BigDecimal("135000"), 6, 4, 35, List.of());
        Prediccion normal = new Prediccion(MANANA, new BigDecimal("100000"), 5, 4, 10, List.of());
        Prediccion sinPromedio = new Prediccion(MANANA, new BigDecimal("100000"), 5, 4, null, List.of());
        PronosticoInsumo uno = insumo("900", "2", PrioridadAlerta.ALTA, "0", null, "1400");
        PronosticoInsumo dos = insumo("800", "2", PrioridadAlerta.ALTA, "0", null, "1400");

        assertThat(GeneradorRecomendaciones.generar(List.of(), ventas(false, fuerte, List.of()))).noneMatch(
                x -> x.tipo() == Recomendacion.Tipo.DEMANDA);
        assertThat(GeneradorRecomendaciones.generar(List.of(), ventas(true, normal, List.of()))).isEmpty();
        assertThat(GeneradorRecomendaciones.generar(List.of(), ventas(true, sinPromedio, List.of()))).isEmpty();

        Recomendacion conUno = GeneradorRecomendaciones.generar(List.of(uno), ventas(true, fuerte, List.of())).get(0);
        assertThat(conUno.detalle()).endsWith("Revise antes el inventario: 1 insumo necesita compra.");
        assertThat(conUno.productoId()).isNull();
        Recomendacion conDos = GeneradorRecomendaciones.generar(List.of(uno, dos), ventas(true, fuerte, List.of())).get(0);
        assertThat(conDos.detalle()).endsWith("2 insumos necesitan compra.");
    }

    @Test
    @DisplayName("Un producto sin comparación o con un cambio pequeño no genera recomendación")
    void productosSinCambioImportante() {
        List<ProductoTendencia> productos = List.of(new ProductoTendencia(1L, "Ajiaco", 10, 0, null),
                new ProductoTendencia(2L, "Jugo", 10, 9, 11));

        assertThat(GeneradorRecomendaciones.generar(List.of(), ventas(true, null, productos))).isEmpty();
    }

    @Test
    @DisplayName("Abreviaturas de todas las unidades y \"1 día\" en singular")
    void unidades() {
        assertThat(List.of(UnidadMedida.values()).stream().map(GeneradorRecomendaciones::abreviatura).toList())
                .containsExactlyInAnyOrder("g", "kg", "ml", "l", "und");
        assertThat(GeneradorRecomendaciones.dias(BigDecimal.ONE)).isEqualTo("1 día");
        assertThat(GeneradorRecomendaciones.dias(new BigDecimal("2.5"))).isEqualTo("2,5 días");
    }

    // ---------- Avisos ----------

    @Test
    @DisplayName("Avisos del mesero: sin hora de Listo o de entrega no hay aviso; uno pagado se ignora")
    void avisosDelMesero() {
        assertThat(GeneradorAvisos.listoSinEntregar(pedido(EstadoCocina.LISTO, null, null), AHORA)).isNull();
        assertThat(GeneradorAvisos.sinCobrar(pedido(EstadoCocina.ENTREGADO, null, null), AHORA)).isNull();

        Aviso atencion = GeneradorAvisos.sinCobrar(
                pedido(EstadoCocina.ENTREGADO, AHORA.minusMinutes(50), AHORA.minusMinutes(40)), AHORA);
        assertThat(atencion.nivel()).isEqualTo(Aviso.Nivel.ATENCION);

        Pedido pagado = new Pedido(9L, 6, 7L, "Laura", pedido(EstadoCocina.ENTREGADO, null, null).getDetalles(), null,
                EstadoCocina.ENTREGADO, EstadoPedido.PAGADO, AHORA.minusHours(3), null, null, AHORA.minusHours(2), null);
        assertThat(GeneradorAvisos.generar(List.of(pagado), TiemposPreparacion.aprender(List.of()), List.of(), null,
                AHORA)).isEmpty();
    }

    @Test
    @DisplayName("Inventario: un insumo que alcanza para pocos días avisa sin sugerir compra si no hay cantidad")
    void avisoDeInsumo() {
        Aviso seAcaba = GeneradorAvisos.insumo(insumo("1500", "2", null, null, null, "1400"), AHORA);
        assertThat(seAcaba.tipo()).isEqualTo(Aviso.Tipo.INSUMO_SE_ACABA);
        assertThat(seAcaba.mensaje()).isEqualTo("Al ritmo actual alcanza para 2 días.");

        assertThat(GeneradorAvisos.insumo(insumo("9000", null, null, null, null, "0"), AHORA)).isNull();
        assertThat(GeneradorAvisos.insumo(insumo("9000", "10", null, null, null, "700"), AHORA)).isNull();
    }

    @Test
    @DisplayName("Demanda: sin datos no hay aviso; sin productos ni variación es solo informativo")
    void avisoDeDemanda() {
        assertThat(GeneradorAvisos.demanda(new Prediccion(MANANA, BigDecimal.ZERO, 0, 4, null, List.of()), AHORA)).isNull();
        assertThat(GeneradorAvisos.demanda(new Prediccion(MANANA, BigDecimal.TEN, 5, 0, null, List.of()), AHORA)).isNull();

        Aviso informativo = GeneradorAvisos.demanda(
                new Prediccion(MANANA, new BigDecimal("100000"), 5, 4, null, List.of()), AHORA);
        assertThat(informativo.nivel()).isEqualTo(Aviso.Nivel.INFO);
        assertThat(informativo.mensaje()).isEqualTo("Se esperan unos 5 pedidos.");
    }

    // ---------- Datos para Gemini ----------

    @Test
    @DisplayName("El contexto para Gemini funciona sin ventas, sin tendencias ni predicción y con insumos sin consumo")
    void contextoIncompleto() {
        String sinVentas = InstruccionesIA.contexto(AHORA, null,
                List.of(insumo("9000", null, null, null, null, "0")), List.of());
        assertThat(sinVentas).doesNotContain("VENTAS").contains("sin consumo").contains("nada");

        Prediccion pocosDatos = new Prediccion(MANANA, new BigDecimal("100000"), 5, 4, null, List.of());
        AnalisisVentas ventas = new AnalisisVentas(3, false, new BigDecimal("90000"), null, null, null,
                List.of(new ProductoTendencia(1L, "Ajiaco", 3, 0, null)), List.of(), pocosDatos);
        String conVentas = InstruccionesIA.contexto(AHORA, ventas, List.of(), List.of());
        assertThat(conVentas).contains("- Ajiaco: 3 / 0\n").doesNotContain("Más pedidos").doesNotContain("INSUMOS");

        AnalisisVentas bajando = new AnalisisVentas(20, true, new BigDecimal("90000"), -15, null, null,
                List.of(), List.of(), new Prediccion(MANANA, BigDecimal.TEN, 5, 4, -5,
                List.of(new DemandaProducto(1L, "Ajiaco", 2))));
        assertThat(InstruccionesIA.contexto(AHORA, bajando, List.of(), List.of()))
                .contains("-15 % frente a la semana anterior").contains("Más pedidos: Ajiaco 2");
        assertThat(InstruccionesIA.sistemaResumen(com.udec.restoflow.domain.model.Rol.ADMIN))
                .contains("administración");
    }
}
