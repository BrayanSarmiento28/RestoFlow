package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarAsistenteIAUseCase.RecomendacionesIA;
import com.udec.restoflow.application.port.in.ConsultarAvisosUseCase;
import com.udec.restoflow.application.port.in.ConsultarAvisosUseCase.AvisosDelMomento;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase.AnalisisRestoFlowAI;
import com.udec.restoflow.application.port.out.AsistenteIAPort;
import com.udec.restoflow.domain.exception.AsistenteIANoDisponibleException;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.ia.AnalisisVentas;
import com.udec.restoflow.domain.ia.Aviso;
import com.udec.restoflow.domain.ia.Recomendacion;
import com.udec.restoflow.domain.ia.RespuestaIA;
import com.udec.restoflow.domain.ia.RespuestaIA.Origen;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IA externa con una IA falsa: caché, plan B y que nunca se rompa la campana por culpa de la IA. */
class AsistenteIAServiceTest {

    /** IA de mentira: responde lo que se le diga o falla, y cuenta las llamadas. */
    static class IAFalsa implements AsistenteIAPort {
        boolean configurado = true;
        boolean falla = false;
        String respuesta = "Atienda primero la mesa 4.";
        final List<String> mensajes = new ArrayList<>();

        @Override
        public boolean configurado() {
            return configurado;
        }

        @Override
        public String generar(String instrucciones, String mensaje) {
            mensajes.add(mensaje);
            if (falla) {
                throw new AsistenteIANoDisponibleException(AsistenteIANoDisponibleException.NO_RESPONDE);
            }
            return respuesta;
        }
    }

    private final IAFalsa ia = new IAFalsa();
    private final Dobles.RelojManual reloj = new Dobles.RelojManual();
    private List<Aviso> avisosActuales;
    private AsistenteIAService servicio;

    private final Recomendacion deReglas = new Recomendacion(Recomendacion.Tipo.COMPRA, PrioridadAlerta.ALTA,
            "Compre pollo", "Calculado con reglas", 1L, null);

    @BeforeEach
    void preparar() {
        avisosActuales = new ArrayList<>(List.of(new Aviso("demora-1", Aviso.Tipo.PLATO_DEMORADO, Aviso.Nivel.URGENTE,
                "Mesa 4 · pedido demorado", "Lleva 40 min", "/cocina", LocalDateTime.now(reloj),
                Set.of(Rol.COCINA), 7L)));
        ConsultarAvisosUseCase avisos = (rol, id) -> new AvisosDelMomento(LocalDateTime.now(reloj), avisosActuales);
        ConsultarRestoFlowAIUseCase analisis = () -> new AnalisisRestoFlowAI(LocalDateTime.now(reloj),
                new AnalisisVentas(0, false, BigDecimal.ZERO, null, null, null, List.of(), List.of(), null),
                List.of(), List.of(deReglas));
        servicio = new AsistenteIAService(ia, avisos, analisis, reloj);
    }

    @Test
    @DisplayName("1 · El resumen lo escribe la IA y se reutiliza 5 minutos mientras los avisos no cambien")
    void resumenConCache() {
        RespuestaIA r = servicio.resumir(Rol.COCINA, 3L);
        assertEquals("Atienda primero la mesa 4.", r.texto());
        assertEquals(Origen.GEMINI, r.origen());
        assertTrue(ia.mensajes.get(0).contains("Mesa 4 · pedido demorado"));

        servicio.resumir(Rol.COCINA, 3L);
        assertEquals(1, ia.mensajes.size());                                   // salió de la caché

        avisosActuales.add(new Aviso("listo-2", Aviso.Tipo.PEDIDO_LISTO, Aviso.Nivel.ATENCION, "Mesa 2", "Listo",
                "/pedidos", LocalDateTime.now(reloj), Set.of(Rol.MESERO), 7L));
        servicio.resumir(Rol.COCINA, 3L);
        assertEquals(2, ia.mensajes.size());                                   // cambiaron los avisos: se redacta otro

        reloj.avanzarMinutos(6);
        servicio.resumir(Rol.COCINA, 3L);
        assertEquals(3, ia.mensajes.size());                                   // venció
    }

    @Test
    @DisplayName("1 · Sin avisos no se gasta una llamada a la IA; si la IA falla, resumen con reglas")
    void resumenPlanB() {
        avisosActuales.clear();
        assertEquals(Origen.REGLAS, servicio.resumir(Rol.MESERO, 7L).origen());
        assertEquals(0, ia.mensajes.size());

        avisosActuales.add(new Aviso("x", Aviso.Tipo.PLATO_DEMORADO, Aviso.Nivel.URGENTE, "Mesa 9", "m", null, null,
                Set.of(Rol.COCINA), null));
        ia.falla = true;
        RespuestaIA r = servicio.resumir(Rol.COCINA, 3L);
        assertEquals(Origen.REGLAS, r.origen());
        assertEquals("Tiene 1 aviso (1 urgente). Empiece por: Mesa 9.", r.texto());

        ia.configurado = false;
        assertEquals(Origen.REGLAS, servicio.resumir(Rol.INVENTARIO, 4L).origen());
    }

    @Test
    @DisplayName("2 · La pregunta llega a la IA con los datos del restaurante; sin IA responde 503")
    void preguntar() {
        ia.respuesta = "Compre **10 kg** de pollo.";
        RespuestaIA r = servicio.preguntar("¿Qué compro esta semana?");
        assertEquals("Compre 10 kg de pollo.", r.texto());                    // sin markdown
        assertTrue(ia.mensajes.get(0).contains("DATOS DEL RESTAURANTE"));
        assertTrue(ia.mensajes.get(0).contains("¿Qué compro esta semana?"));

        assertThrows(DomainException.class, () -> servicio.preguntar(""));
        ia.configurado = false;
        AsistenteIANoDisponibleException e = assertThrows(AsistenteIANoDisponibleException.class,
                () -> servicio.preguntar("¿Hola?"));
        assertEquals(AsistenteIANoDisponibleException.SIN_CLAVE, e.getMessage());
    }

    @Test
    @DisplayName("3 · Recomendaciones de la IA (30 min en caché); si responde mal o falla, las de reglas")
    void recomendaciones() {
        ia.respuesta = "ALTA|COMPRA|Comprar pollo hoy|Quedan 2 kg.\nMEDIA|DEMANDA|Sábado fuerte|42 pedidos.";
        RecomendacionesIA r = servicio.recomendaciones();
        assertEquals(Origen.GEMINI, r.origen());
        assertEquals(2, r.recomendaciones().size());
        servicio.recomendaciones();
        assertEquals(1, ia.mensajes.size());

        reloj.avanzarMinutos(31);
        ia.respuesta = "No entendí la tarea";
        RecomendacionesIA mal = servicio.recomendaciones();
        assertEquals(Origen.REGLAS, mal.origen());
        assertEquals(List.of(deReglas), mal.recomendaciones());
    }
}
