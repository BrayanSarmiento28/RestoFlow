package com.udec.restoflow.domain;

import com.udec.restoflow.domain.ia.AnalisisVentas.DemandaProducto;
import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.Aviso;
import com.udec.restoflow.domain.ia.Aviso.Nivel;
import com.udec.restoflow.domain.ia.Aviso.Tipo;
import com.udec.restoflow.domain.ia.GeneradorAvisos;
import com.udec.restoflow.domain.ia.PronosticoInsumo;
import com.udec.restoflow.domain.ia.TiemposPreparacion;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RestoFlow AI · avisos por rol (Java puro, sin base de datos). */
class AvisosDominioTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 6, 13, 0);   // viernes 1 p. m.
    private static final long BANDEJA = 1L;
    private static final long JUGO = 2L;
    private static final long LAURA = 7L;
    private static final long PEDRO = 8L;

    private static DetallePedido bandeja() {
        return new DetallePedido(null, BANDEJA, "Bandeja paisa", 1, new BigDecimal("32000"), null);
    }

    private static DetallePedido jugo() {
        return new DetallePedido(null, JUGO, "Jugo natural", 1, new BigDecimal("7000"), null);
    }

    private static Pedido pedido(long id, int mesa, long mesero, EstadoCocina estado, LocalDateTime creado,
                                 LocalDateTime listo, LocalDateTime entregado, DetallePedido... lineas) {
        return new Pedido(id, mesa, mesero, "Mesero", List.of(lineas), null, estado, EstadoPedido.ABIERTO, creado,
                estado == EstadoCocina.PENDIENTE ? null : creado, listo, entregado, null);
    }

    /** Historial: la bandeja sale en 20, 22, 25 y 120 min (un día se olvidó) → mediana 24 min. */
    private static TiemposPreparacion historial() {
        List<Pedido> lista = new ArrayList<>();
        int[] minutos = {20, 22, 25, 120};
        for (int i = 0; i < minutos.length; i++) {
            LocalDateTime creado = AHORA.minusDays(1 + i);
            lista.add(new Pedido(100L + i, 1, LAURA, "Laura", List.of(bandeja()), null, EstadoCocina.ENTREGADO,
                    EstadoPedido.PAGADO, creado, creado, creado.plusMinutes(minutos[i]), creado.plusMinutes(130),
                    creado.plusMinutes(140)));
        }
        return TiemposPreparacion.aprender(lista);
    }

    private static PronosticoInsumo insumo(long id, String nombre, String stock, String minimo, String dias) {
        return new PronosticoInsumo(id, nombre, UnidadMedida.KILOGRAMO, new BigDecimal(stock), new BigDecimal(minimo),
                BigDecimal.ONE, new BigDecimal("7"), 0, dias == null ? null : new BigDecimal(dias),
                PrioridadAlerta.ALTA, new BigDecimal("10"));
    }

    @Test
    @DisplayName("El tiempo normal de cada plato es la mediana real: un pedido olvidado no lo dispara")
    void tiemposAprendidos() {
        TiemposPreparacion t = historial();
        assertEquals(24, t.minutosNormales(BANDEJA));                       // (22 + 25) / 2, no el promedio de 47
        assertTrue(t.tieneDatos(BANDEJA));
        assertEquals(TiemposPreparacion.MINUTOS_POR_DEFECTO, t.minutosNormales(JUGO));   // sin datos: 15 min
        assertFalse(t.tieneDatos(JUGO));
    }

    @Test
    @DisplayName("Cocina: el pedido se marca demorado al pasar su tiempo normal y urgente al pasar 1,5 veces")
    void platoDemorado() {
        TiemposPreparacion t = historial();
        Pedido aTiempo = pedido(1, 3, LAURA, EstadoCocina.EN_PREPARACION, AHORA.minusMinutes(20), null, null, bandeja());
        Pedido demorado = pedido(2, 4, LAURA, EstadoCocina.EN_PREPARACION, AHORA.minusMinutes(30), null, null,
                bandeja(), jugo());
        Pedido urgente = pedido(3, 5, PEDRO, EstadoCocina.PENDIENTE, AHORA.minusMinutes(40), null, null, bandeja());

        List<Aviso> avisos = GeneradorAvisos.generar(List.of(aTiempo, demorado, urgente), t, List.of(), null, AHORA);

        assertEquals(2, avisos.size());
        Aviso primero = avisos.get(0);                                       // el urgente va primero
        assertEquals("demora-3", primero.clave());
        assertEquals("Mesa 5 · pedido demorado", primero.titulo());          // la mesa, no el número del pedido
        assertEquals(Nivel.URGENTE, primero.nivel());
        assertTrue(primero.mensaje().contains("Lleva 40 min y lo normal para Bandeja paisa son 24 min"));
        assertTrue(primero.mensaje().contains("Aún no se empieza"));
        assertEquals(Nivel.ATENCION, avisos.get(1).nivel());
        assertEquals("/cocina", avisos.get(1).enlace());
    }

    @Test
    @DisplayName("Mesero: pedido listo sin llevar (3 y 8 min) y entregado sin cobrar (30 y 60 min)")
    void avisosDelMesero() {
        List<Pedido> abiertos = List.of(
                pedido(1, 1, LAURA, EstadoCocina.LISTO, AHORA.minusMinutes(30), AHORA.minusMinutes(2), null, jugo()),
                pedido(2, 2, LAURA, EstadoCocina.LISTO, AHORA.minusMinutes(30), AHORA.minusMinutes(5), null, jugo()),
                pedido(3, 3, LAURA, EstadoCocina.LISTO, AHORA.minusMinutes(30), AHORA.minusMinutes(9), null, jugo()),
                pedido(4, 4, PEDRO, EstadoCocina.ENTREGADO, AHORA.minusMinutes(90), AHORA.minusMinutes(80),
                        AHORA.minusMinutes(35), bandeja()),
                pedido(5, 5, PEDRO, EstadoCocina.ENTREGADO, AHORA.minusMinutes(90), AHORA.minusMinutes(80),
                        AHORA.minusMinutes(10), bandeja()));

        List<Aviso> avisos = GeneradorAvisos.generar(abiertos, historial(), List.of(), null, AHORA);

        // Primero lo urgente; entre iguales, lo que lleva más tiempo esperando
        assertEquals(List.of("listo-3", "cobro-4", "listo-2"), avisos.stream().map(Aviso::clave).toList());
        assertEquals(Nivel.URGENTE, avisos.get(0).nivel());
        assertEquals(Tipo.SIN_COBRAR, avisos.get(1).tipo());
        assertTrue(avisos.get(1).mensaje().contains("$ 32.000"));
    }

    @Test
    @DisplayName("Inventario: urgente si el insumo está en su mínimo y atención si alcanza para 3 días o menos")
    void avisosDeInventario() {
        List<PronosticoInsumo> insumos = List.of(
                insumo(1, "Pollo", "2", "3", "1"),
                insumo(2, "Arroz", "10", "2", "2.5"),
                insumo(3, "Sal", "10", "1", "20"));

        List<Aviso> avisos = GeneradorAvisos.generar(List.of(), historial(), insumos, null, AHORA);

        assertEquals(2, avisos.size());
        assertEquals("Pollo está en su mínimo", avisos.get(0).titulo());
        assertEquals("Quedan 2 kg (mínimo 3 kg). Sugerido: comprar 10 kg.", avisos.get(0).mensaje());
        assertEquals("Al ritmo actual alcanza para 2,5 días. Sugerido: comprar 10 kg.", avisos.get(1).mensaje());
    }

    @Test
    @DisplayName("Demanda: si mañana se espera 20 % más que un día normal, se avisa a inventario y cocina")
    void demandaDeManana() {
        LocalDate sabado = AHORA.toLocalDate().plusDays(1);
        List<DemandaProducto> productos = List.of(new DemandaProducto(BANDEJA, "Bandeja paisa", 30),
                new DemandaProducto(JUGO, "Jugo natural", 25));
        Prediccion fuerte = new Prediccion(sabado, new BigDecimal("1500000"), 42, 4, 25, productos);
        Prediccion normal = new Prediccion(sabado, new BigDecimal("1000000"), 30, 4, 5, productos);

        Aviso aviso = GeneradorAvisos.generar(List.of(), historial(), List.of(), fuerte, AHORA).get(0);
        assertEquals("Mañana sábado será un día fuerte", aviso.titulo());
        assertTrue(aviso.mensaje().contains("unos 42 pedidos, 25 % más"));
        assertTrue(aviso.mensaje().contains("Bandeja paisa (30), Jugo natural (25)"));
        assertTrue(aviso.esPara(Rol.INVENTARIO, null));

        Aviso informativo = GeneradorAvisos.generar(List.of(), historial(), List.of(), normal, AHORA).get(0);
        assertEquals(Nivel.INFO, informativo.nivel());
        assertFalse(informativo.esPara(Rol.COCINA, null));                  // un día normal solo lo ve el admin
        assertTrue(informativo.esPara(Rol.ADMIN, null));
    }

    @Test
    @DisplayName("Cada rol ve lo suyo: el mesero solo sus mesas y el admin todo")
    void destinatarios() {
        List<Pedido> abiertos = List.of(
                pedido(1, 1, LAURA, EstadoCocina.LISTO, AHORA.minusMinutes(30), AHORA.minusMinutes(5), null, jugo()),
                pedido(2, 2, PEDRO, EstadoCocina.EN_PREPARACION, AHORA.minusMinutes(18), null, null, jugo()),
                pedido(3, 3, PEDRO, EstadoCocina.EN_PREPARACION, AHORA.minusMinutes(60), null, null, jugo()));
        List<Aviso> avisos = GeneradorAvisos.generar(abiertos, historial(), List.of(insumo(1, "Pollo", "1", "3", "1")),
                null, AHORA);

        assertEquals(List.of("demora-3", "demora-2"), claves(avisos, Rol.COCINA, null));
        assertEquals(List.of("minimo-1"), claves(avisos, Rol.INVENTARIO, null));
        assertEquals(List.of("listo-1"), claves(avisos, Rol.MESERO, LAURA));
        assertEquals(List.of("demora-3"), claves(avisos, Rol.MESERO, PEDRO));   // solo lo urgente de su mesa
        assertEquals(4, claves(avisos, Rol.ADMIN, 1L).size());
    }

    private static List<String> claves(List<Aviso> avisos, Rol rol, Long usuario) {
        return avisos.stream().filter(a -> a.esPara(rol, usuario)).map(Aviso::clave).toList();
    }
}
