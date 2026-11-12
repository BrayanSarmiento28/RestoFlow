package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.ia.AnalisisVentas;
import com.udec.restoflow.domain.ia.AnalisisVentas.DemandaProducto;
import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.AnalisisVentas.ProductoTendencia;
import com.udec.restoflow.domain.ia.Aviso;
import com.udec.restoflow.domain.ia.InstruccionesIA;
import com.udec.restoflow.domain.ia.PronosticoInsumo;
import com.udec.restoflow.domain.ia.Recomendacion;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IA externa · qué se le envía a Gemini, cómo se lee lo que responde y el plan B sin IA. */
class InstruccionesIATest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 6, 13, 0);

    private static Aviso aviso(String clave, Aviso.Nivel nivel, String titulo) {
        return new Aviso(clave, Aviso.Tipo.PLATO_DEMORADO, nivel, titulo, "Lleva 30 min", "/cocina", AHORA,
                Set.of(Rol.COCINA), 7L);
    }

    @Test
    @DisplayName("Lee las recomendaciones de la IA e ignora las líneas que no cumplen el formato")
    void leerRecomendaciones() {
        String respuesta = """
                Aquí tiene mis recomendaciones:
                1. ALTA|COMPRA|Comprar pollo hoy|Quedan 2 kg y se gasta 1 kg al día.
                - **MEDIA**|DEMANDA|Prepararse para el sábado|Se esperan 42 pedidos, 25 % más.
                BAJA|PROMOCIÓN|Impulsar el jugo|Bajó 75 % frente a la semana anterior.
                URGENTE|COMPRA|Prioridad inventada|No debe salir.
                ALTA|COMPRA||Sin título no sirve.
                """;

        List<Recomendacion> r = InstruccionesIA.leerRecomendaciones(respuesta);

        assertEquals(3, r.size());
        assertEquals(PrioridadAlerta.ALTA, r.get(0).prioridad());
        assertEquals(Recomendacion.Tipo.COMPRA, r.get(0).tipo());
        assertEquals("Comprar pollo hoy", r.get(0).titulo());
        assertEquals(Recomendacion.Tipo.DEMANDA, r.get(1).tipo());           // quitó el "- **" del inicio
        assertEquals(Recomendacion.Tipo.INFORMACION, r.get(2).tipo());       // un tipo desconocido no se pierde
        assertTrue(InstruccionesIA.leerRecomendaciones("La IA respondió otra cosa").isEmpty());
        assertTrue(InstruccionesIA.leerRecomendaciones(null).isEmpty());
    }

    @Test
    @DisplayName("Los datos para la IA son del negocio (ventas, platos, insumos, avisos) y en texto corto")
    void contexto() {
        AnalisisVentas ventas = new AnalisisVentas(20, true, new BigDecimal("1250000"), 12, DayOfWeek.SATURDAY, 13,
                List.of(new ProductoTendencia(1L, "Bandeja paisa", 30, 25, 20)), List.of(),
                new Prediccion(AHORA.toLocalDate().plusDays(1), new BigDecimal("1500000"), 42, 4, 25,
                        List.of(new DemandaProducto(1L, "Bandeja paisa", 30))));
        PronosticoInsumo pollo = new PronosticoInsumo(1L, "Pollo", UnidadMedida.KILOGRAMO, new BigDecimal("2"),
                new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("7"), 0, new BigDecimal("2"), PrioridadAlerta.ALTA,
                new BigDecimal("10"));

        String texto = InstruccionesIA.contexto(AHORA, ventas, List.of(pollo),
                List.of(aviso("demora-1", Aviso.Nivel.URGENTE, "Mesa 4 · pedido demorado")));

        assertTrue(texto.contains("viernes"));
        assertTrue(texto.contains("$ 1.250.000 (+12 % frente a la semana anterior)"));
        assertTrue(texto.contains("Bandeja paisa: 30 / 25 (+20 %)"));
        assertTrue(texto.contains("unos 42 pedidos"));
        assertTrue(texto.contains("Pollo: 2 kg / 3 kg / 1 kg / 2 días / 10 kg"));
        assertTrue(texto.contains("[URGENTE] Mesa 4 · pedido demorado"));
        assertFalse(texto.contains("@"));                                     // ningún correo
    }

    @Test
    @DisplayName("Plan B sin IA: resumen armado con reglas")
    void resumenSinIA() {
        assertEquals("Todo en orden por ahora. RestoFlow AI le avisará aquí si algo necesita su atención.",
                InstruccionesIA.resumenSinIA(List.of()));
        assertEquals("Tiene 2 avisos (1 urgente). Empiece por: Mesa 4 · pedido demorado.",
                InstruccionesIA.resumenSinIA(List.of(aviso("a", Aviso.Nivel.URGENTE, "Mesa 4 · pedido demorado"),
                        aviso("b", Aviso.Nivel.ATENCION, "Mesa 2"))));
    }

    @Test
    @DisplayName("Cada rol recibe instrucciones de su área y la pregunta se valida")
    void instruccionesYPregunta() {
        assertTrue(InstruccionesIA.sistemaResumen(Rol.COCINA).contains("personal de cocina"));
        assertTrue(InstruccionesIA.sistemaResumen(Rol.MESERO).contains("meseros"));
        assertTrue(InstruccionesIA.sistemaPregunta().contains("no lo inventes"));
        assertEquals("¿Qué compro?", InstruccionesIA.validarPregunta("  ¿Qué compro?  "));
        assertThrows(DomainException.class, () -> InstruccionesIA.validarPregunta(" "));
        String muyLarga = "x".repeat(501);
        assertThrows(DomainException.class, () -> InstruccionesIA.validarPregunta(muyLarga));
    }

    @Test
    @DisplayName("Quita el markdown que la pantalla no muestra y acota respuestas muy largas")
    void limpiar() {
        assertEquals("Compre pollo.\n\nPrepare arroz.", InstruccionesIA.limpiar("**Compre** pollo.\n\n\n\nPrepare arroz."));
        assertEquals("Hola", InstruccionesIA.limpiar("# Hola"));
        assertEquals(1500, InstruccionesIA.limpiar("a".repeat(5000)).length());
        assertEquals("", InstruccionesIA.limpiar(null));
    }
}
