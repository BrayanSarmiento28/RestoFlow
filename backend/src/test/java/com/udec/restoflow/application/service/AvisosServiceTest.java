package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarAvisosUseCase.AvisosDelMomento;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase.AnalisisRestoFlowAI;
import com.udec.restoflow.domain.ia.AnalisisVentas;
import com.udec.restoflow.domain.ia.Aviso;
import com.udec.restoflow.domain.ia.PronosticoInsumo;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Avisos con dobles en memoria: qué recibe cada rol y que al mesero no se le calcula el análisis de inventario. */
class AvisosServiceTest {

    private static final long LAURA = 7L;

    private DoblesPedidos.PedidosEnMemoria pedidos;
    private Dobles.RelojManual reloj;
    private final AtomicInteger analisisHechos = new AtomicInteger();

    /** RestoFlow AI falso: un insumo en su mínimo y sin predicción. */
    private final ConsultarRestoFlowAIUseCase restoFlowAI = () -> {
        analisisHechos.incrementAndGet();
        PronosticoInsumo pollo = new PronosticoInsumo(1L, "Pollo", UnidadMedida.KILOGRAMO, BigDecimal.ONE,
                new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("7"), 0, BigDecimal.ONE, PrioridadAlerta.ALTA,
                new BigDecimal("10"));
        AnalisisVentas ventas = new AnalisisVentas(0, false, BigDecimal.ZERO, null, null, null, List.of(), List.of(),
                null);
        return new AnalisisRestoFlowAI(LocalDateTime.now(reloj), ventas, List.of(pollo), List.of());
    };

    private AvisosService servicio;

    @BeforeEach
    void preparar() {
        pedidos = new DoblesPedidos.PedidosEnMemoria();
        reloj = new Dobles.RelojManual();
        servicio = new AvisosService(pedidos, restoFlowAI, reloj);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        DetallePedido jugo = new DetallePedido(null, 2L, "Jugo natural", 1, new BigDecimal("7000"), null);
        // En cocina hace 40 min (lo normal sin historial son 15) y otro listo hace 10 min sin llevar
        pedidos.guardar(new Pedido(null, 4, LAURA, "Laura", List.of(jugo), null, EstadoCocina.EN_PREPARACION,
                EstadoPedido.ABIERTO, ahora.minusMinutes(40), ahora.minusMinutes(35), null, null, null));
        pedidos.guardar(new Pedido(null, 6, LAURA, "Laura", List.of(jugo), null, EstadoCocina.LISTO,
                EstadoPedido.ABIERTO, ahora.minusMinutes(30), ahora.minusMinutes(25), ahora.minusMinutes(10), null,
                null));
    }

    private List<String> claves(AvisosDelMomento r) {
        return r.avisos().stream().map(Aviso::clave).toList();
    }

    @Test
    @DisplayName("La cocina ve el pedido demorado; el inventario, el insumo en su mínimo; el admin, todo")
    void porRol() {
        assertEquals(List.of("demora-1"), claves(servicio.avisosPara(Rol.COCINA, 3L)));
        assertEquals(List.of("minimo-1"), claves(servicio.avisosPara(Rol.INVENTARIO, 4L)));
        assertEquals(3, servicio.avisosPara(Rol.ADMIN, 1L).avisos().size());
    }

    @Test
    @DisplayName("El mesero ve solo sus mesas y no se le calcula el análisis de inventario (más rápido)")
    void mesero() {
        AvisosDelMomento laura = servicio.avisosPara(Rol.MESERO, LAURA);
        assertEquals(List.of("demora-1", "listo-2"), claves(laura));
        assertEquals(0, analisisHechos.get());

        assertEquals(List.of(), claves(servicio.avisosPara(Rol.MESERO, 99L)));
        assertEquals(LocalDateTime.now(reloj), laura.generadoEn());
    }

    @Test
    @DisplayName("Cuando el pedido se entrega, el aviso desaparece solo (no hay nada que marcar)")
    void seResuelveSolo() {
        Pedido listo = pedidos.buscarPorId(2L).orElseThrow();
        listo.cambiarEstadoCocina(EstadoCocina.ENTREGADO, LocalDateTime.now(reloj));
        pedidos.guardar(listo);

        assertEquals(List.of("demora-1"), claves(servicio.avisosPara(Rol.MESERO, LAURA)));
    }
}
