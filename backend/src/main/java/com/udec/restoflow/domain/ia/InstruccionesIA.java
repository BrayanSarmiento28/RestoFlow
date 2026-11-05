package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.ia.AnalisisVentas.Prediccion;
import com.udec.restoflow.domain.ia.AnalisisVentas.ProductoTendencia;
import com.udec.restoflow.domain.ia.Recomendacion.Tipo;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.Rol;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * RestoFlow AI con IA externa (Gemini) · qué se le pide, con qué datos y cómo se lee lo que responde.
 * <p>
 * Reglas de diseño (para la sustentación):
 * <ul>
 *   <li><b>Las reglas deciden, la IA explica.</b> Los avisos y los números los calcula RestoFlow (exactos y
 *       probados); a la IA solo se le pide redactarlos, priorizarlos y responder preguntas con esos datos.</li>
 *   <li><b>Solo datos del negocio.</b> Platos, tiempos, insumos y ventas. Nunca nombres, correos ni contraseñas.</li>
 *   <li><b>No inventar.</b> Se le ordena usar solo los datos entregados y decir cuándo falta información.</li>
 *   <li><b>Plan B.</b> Si la IA no responde, cada función tiene una versión sin IA (ver {@link #resumenSinIA}).</li>
 * </ul>
 * Java puro: se prueba sin internet.
 */
public final class InstruccionesIA {

    public static final int PREGUNTA_MAXIMA = 500;
    static final int RESPUESTA_MAXIMA = 1500;
    static final int RECOMENDACIONES_MAXIMAS = 6;
    private static final int INSUMOS_EN_CONTEXTO = 15;
    private static final int AVISOS_EN_CONTEXTO = 15;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static final String BASE = """
            Eres RestoFlow AI, el asistente de un restaurante colombiano que usa el software RestoFlow.
            Respondes en español de Colombia, claro y breve, tratando de usted.
            Usa SOLO los datos que se te entregan; si falta un dato, dilo y no lo inventes.
            No uses markdown, asteriscos, viñetas ni emojis. Los montos son pesos colombianos.""";

    private InstruccionesIA() {
    }

    // ---------- 1. Resumen de la campana por rol ----------

    public static String sistemaResumen(Rol rol) {
        return BASE + "\nEscribe un resumen de máximo 3 frases (máximo 60 palabras) para el personal de "
                + area(rol) + ": qué debe atender primero y por qué, citando mesas, platos o insumos de los avisos. "
                + "Habla directamente a la persona. Si no hay avisos, dile que todo está en orden.";
    }

    public static String mensajeResumen(List<Aviso> avisos, LocalDateTime ahora) {
        return "Hora actual: " + ahora.format(FECHA) + "\nAvisos del momento:\n" + listaAvisos(avisos);
    }

    /** Plan B sin IA: un resumen armado con reglas a partir de los mismos avisos. */
    public static String resumenSinIA(List<Aviso> avisos) {
        if (avisos.isEmpty()) {
            return "Todo en orden por ahora. RestoFlow AI le avisará aquí si algo necesita su atención.";
        }
        long urgentes = avisos.stream().filter(a -> a.nivel() == Aviso.Nivel.URGENTE).count();
        String cuantos = avisos.size() == 1 ? "Tiene 1 aviso" : "Tiene " + avisos.size() + " avisos";
        String detalle = urgentes == 0 ? "" : urgentes == 1 ? " (1 urgente)" : " (" + urgentes + " urgentes)";
        return cuantos + detalle + ". Empiece por: " + avisos.get(0).titulo() + ".";
    }

    // ---------- 2. Pregúntale a RestoFlow AI (administrador) ----------

    public static String sistemaPregunta() {
        return BASE + "\nResponde la pregunta del administrador usando los datos del restaurante, en máximo "
                + "120 palabras, con números concretos cuando los haya. Si la pregunta no tiene relación con el "
                + "restaurante, responde amablemente que solo puede ayudar con la operación del restaurante.";
    }

    public static String mensajePregunta(String contexto, String pregunta) {
        return "DATOS DEL RESTAURANTE\n" + contexto + "\n\nPREGUNTA DEL ADMINISTRADOR\n" + pregunta;
    }

    /** La pregunta no puede estar vacía ni ser muy larga (se cobra por tamaño y evita abusos). */
    public static String validarPregunta(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            throw new DomainException("Escriba una pregunta");
        }
        String limpia = pregunta.trim();
        if (limpia.length() > PREGUNTA_MAXIMA) {
            throw new DomainException("La pregunta admite máximo " + PREGUNTA_MAXIMA + " caracteres");
        }
        return limpia;
    }

    // ---------- 3. Recomendaciones de la pantalla RestoFlow AI redactadas por la IA ----------

    public static String sistemaAnalisis() {
        return BASE + """

                Analiza los datos y da entre 3 y 5 recomendaciones concretas para el administrador, de la más a la \
                menos importante. Escribe cada recomendación en UNA línea con este formato exacto, sin numerar ni \
                agregar texto antes o después:
                PRIORIDAD|TIPO|Título corto|Explicación de una o dos frases con los números que la justifican
                PRIORIDAD es ALTA, MEDIA o BAJA. TIPO es COMPRA, DEMANDA, PRODUCTO, TENDENCIA o INFORMACION.""";
    }

    public static String mensajeAnalisis(String contexto) {
        return "DATOS DEL RESTAURANTE\n" + contexto;
    }

    /**
     * Lee las líneas "PRIORIDAD|TIPO|Título|Detalle". Las que no cumplen el formato se ignoran; si ninguna sirve,
     * la lista queda vacía y se usan las recomendaciones calculadas con reglas.
     */
    public static List<Recomendacion> leerRecomendaciones(String texto) {
        List<Recomendacion> lista = new ArrayList<>();
        if (texto == null) {
            return lista;
        }
        for (String linea : texto.split("\\R")) {
            String l = linea.replaceAll("^[\\s\\-*•\\d.)]+", "").replace("**", "").trim();
            String[] partes = l.split("\\|", 4);
            if (partes.length < 4 || partes[2].isBlank() || partes[3].isBlank()) {
                continue;
            }
            PrioridadAlerta prioridad = enumOrNull(PrioridadAlerta.class, partes[0]);
            if (prioridad == null) {
                continue;
            }
            Tipo tipo = enumOrNull(Tipo.class, partes[1]);
            lista.add(new Recomendacion(tipo == null ? Tipo.INFORMACION : tipo, prioridad,
                    recortar(partes[2].trim(), 120), recortar(partes[3].trim(), 400), null, null));
            if (lista.size() == RECOMENDACIONES_MAXIMAS) {
                break;
            }
        }
        return lista;
    }

    // ---------- Datos que se le entregan a la IA ----------

    /** Los datos del restaurante en texto corto (sin datos personales). */
    public static String contexto(LocalDateTime ahora, AnalisisVentas ventas, List<PronosticoInsumo> insumos,
                                  List<Aviso> avisos) {
        StringBuilder t = new StringBuilder();
        t.append("Fecha y hora: ").append(ahora.format(FECHA)).append(" (")
                .append(ahora.getDayOfWeek().getDisplayName(TextStyle.FULL, COLOMBIA)).append(")\n");
        if (ventas != null) {
            t.append("VENTAS de los últimos 7 días completos: ").append(GeneradorRecomendaciones.pesos(ventas.ventasSemana()));
            if (ventas.tendenciaVentas() != null) {
                t.append(" (").append(signo(ventas.tendenciaVentas())).append(" frente a la semana anterior)");
            }
            t.append(". Días con ventas en las últimas 4 semanas: ").append(ventas.diasConVentas()).append(".");
            if (ventas.diaMasFuerte() != null) {
                t.append(" Día más fuerte: ").append(ventas.diaMasFuerte().getDisplayName(TextStyle.FULL, COLOMBIA)).append(".");
            }
            if (ventas.horaPico() != null) {
                t.append(" Hora pico: ").append(ventas.horaPico()).append(":00.");
            }
            t.append("\nPRODUCTOS (unidades esta semana / semana anterior):\n");
            for (ProductoTendencia p : ventas.productos()) {
                t.append("- ").append(p.nombre()).append(": ").append(p.unidadesSemana()).append(" / ")
                        .append(p.unidadesSemanaAnterior());
                if (p.tendenciaPorcentaje() != null) {
                    t.append(" (").append(signo(p.tendenciaPorcentaje())).append(")");
                }
                t.append("\n");
            }
            Prediccion pr = ventas.prediccion();
            if (pr != null) {
                t.append("PREDICCIÓN para ").append(pr.fecha().getDayOfWeek().getDisplayName(TextStyle.FULL, COLOMBIA))
                        .append(" ").append(pr.fecha()).append(": unos ").append(pr.pedidosEsperados())
                        .append(" pedidos y ").append(GeneradorRecomendaciones.pesos(pr.ventasEsperadas()));
                if (pr.variacionPromedio() != null) {
                    t.append(" (").append(signo(pr.variacionPromedio())).append(" frente a un día normal)");
                }
                String mas = pr.productos().stream().map(d -> d.nombre() + " " + d.unidades())
                        .collect(Collectors.joining(", "));
                t.append(mas.isEmpty() ? "" : ". Más pedidos: " + mas).append("\n");
            }
        }
        if (!insumos.isEmpty()) {
            t.append("INSUMOS (stock / mínimo / consumo diario / días que alcanza / sugerido comprar):\n");
            insumos.stream().limit(INSUMOS_EN_CONTEXTO).forEach(i -> {
                String u = " " + GeneradorRecomendaciones.abreviatura(i.unidad());
                t.append("- ").append(i.nombre()).append(": ").append(num(i.stockActual())).append(u).append(" / ")
                        .append(num(i.stockMinimo())).append(u).append(" / ").append(num(i.consumoDiario())).append(u)
                        .append(" / ").append(i.diasCobertura() == null ? "sin consumo" : num(i.diasCobertura()) + " días")
                        .append(" / ").append(i.cantidadSugerida() == null ? "nada" : num(i.cantidadSugerida()) + u)
                        .append("\n");
            });
        }
        t.append("AVISOS DEL MOMENTO:\n").append(listaAvisos(avisos));
        return t.toString();
    }

    /** Quita formato que la pantalla no muestra (markdown) y acota el largo. */
    public static String limpiar(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.replace("**", "").replace("__", "")
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return recortar(limpio, RESPUESTA_MAXIMA);
    }

    // ---------- Apoyo ----------

    static String area(Rol rol) {
        return switch (rol) {
            case COCINA -> "cocina";
            case MESERO -> "meseros (atención de las mesas)";
            case INVENTARIO -> "inventario y compras";
            case ADMIN -> "administración (ve todo el restaurante)";
        };
    }

    private static String listaAvisos(List<Aviso> avisos) {
        if (avisos.isEmpty()) {
            return "(ninguno)\n";
        }
        return avisos.stream().limit(AVISOS_EN_CONTEXTO)
                .map(a -> "- [" + a.nivel() + "] " + a.titulo() + ": " + a.mensaje())
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static String signo(int porcentaje) {
        return (porcentaje > 0 ? "+" : "") + porcentaje + " %";
    }

    private static String num(BigDecimal valor) {
        return valor == null ? "-" : GeneradorRecomendaciones.numero(valor);
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1).trim() + "…";
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> tipo, String valor) {
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT)
                    .replace("Ó", "O").replace("Í", "I").replace("Á", "A"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
