package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarAsistenteIAUseCase;
import com.udec.restoflow.application.port.in.ConsultarAvisosUseCase;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase;
import com.udec.restoflow.application.port.in.ConsultarRestoFlowAIUseCase.AnalisisRestoFlowAI;
import com.udec.restoflow.application.port.in.ResumirAvisosIAUseCase;
import com.udec.restoflow.application.port.out.AsistenteIAPort;
import com.udec.restoflow.domain.exception.AsistenteIANoDisponibleException;
import com.udec.restoflow.domain.ia.Aviso;
import com.udec.restoflow.domain.ia.InstruccionesIA;
import com.udec.restoflow.domain.ia.Recomendacion;
import com.udec.restoflow.domain.ia.RespuestaIA;
import com.udec.restoflow.domain.ia.RespuestaIA.Origen;
import com.udec.restoflow.domain.model.Rol;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * RestoFlow AI con IA externa (Gemini), en tres funciones:
 * <ol>
 *   <li>Resumen de la campana para cada rol.</li>
 *   <li>Preguntas libres del administrador.</li>
 *   <li>Recomendaciones de la pantalla RestoFlow AI redactadas por la IA.</li>
 * </ol>
 * Los datos los calcula RestoFlow (avisos y análisis); la IA solo los redacta. Las respuestas se guardan unos
 * minutos en memoria para no gastar el cupo gratuito de la IA cada vez que alguien abre la campana.
 * <p>
 * A propósito <b>no</b> es {@code @Transactional}: la IA puede tardar varios segundos y no se debe tener una
 * conexión a la base de datos ocupada mientras tanto. Cada consulta de datos abre y cierra la suya.
 */
public class AsistenteIAService implements ResumirAvisosIAUseCase, ConsultarAsistenteIAUseCase {

    static final Duration VIGENCIA_RESUMEN = Duration.ofMinutes(5);
    static final Duration VIGENCIA_RECOMENDACIONES = Duration.ofMinutes(30);
    /** Si la IA falló, se espera un poco antes de volver a intentarlo. */
    static final Duration ESPERA_TRAS_FALLA = Duration.ofMinutes(1);
    private static final int MAXIMO_EN_MEMORIA = 200;

    private final AsistenteIAPort ia;
    private final ConsultarAvisosUseCase avisos;
    private final ConsultarRestoFlowAIUseCase restoFlowAI;
    private final Clock reloj;

    private final Map<String, Guardado<RespuestaIA>> resumenes = new ConcurrentHashMap<>();
    private final AtomicReference<Guardado<RecomendacionesIA>> recomendaciones = new AtomicReference<>();

    public AsistenteIAService(AsistenteIAPort ia, ConsultarAvisosUseCase avisos, ConsultarRestoFlowAIUseCase restoFlowAI,
                              Clock reloj) {
        this.ia = ia;
        this.avisos = avisos;
        this.restoFlowAI = restoFlowAI;
        this.reloj = reloj;
    }

    // ---------- 1. Resumen de la campana ----------

    @Override
    public RespuestaIA resumir(Rol rol, Long usuarioId) {
        List<Aviso> lista = avisos.avisosPara(rol, usuarioId).avisos();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (lista.isEmpty()) {
            return new RespuestaIA(InstruccionesIA.resumenSinIA(lista), Origen.REGLAS, ahora);   // nada que redactar
        }
        String clave = rol + ":" + usuarioId;
        String firma = lista.stream().map(a -> a.clave() + a.nivel()).collect(Collectors.joining(","));
        Guardado<RespuestaIA> guardado = resumenes.get(clave);
        if (guardado != null && guardado.firma().equals(firma) && guardado.vigente(reloj.instant())) {
            return guardado.valor();
        }
        RespuestaIA respuesta;
        Duration vigencia = VIGENCIA_RESUMEN;
        try {
            String texto = llamar("resumen", InstruccionesIA.sistemaResumen(rol),
                    InstruccionesIA.mensajeResumen(lista, ahora));
            respuesta = new RespuestaIA(texto, Origen.GEMINI, ahora);
        } catch (AsistenteIANoDisponibleException e) {
            respuesta = new RespuestaIA(InstruccionesIA.resumenSinIA(lista), Origen.REGLAS, ahora);
            vigencia = ESPERA_TRAS_FALLA;
        }
        if (resumenes.size() >= MAXIMO_EN_MEMORIA) {
            resumenes.clear();
        }
        resumenes.put(clave, new Guardado<>(firma, respuesta, reloj.instant().plus(vigencia)));
        return respuesta;
    }

    // ---------- 2. Preguntas del administrador ----------

    @Override
    public RespuestaIA preguntar(String pregunta) {
        String limpia = InstruccionesIA.validarPregunta(pregunta);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        String texto = llamar("pregunta", InstruccionesIA.sistemaPregunta(),
                InstruccionesIA.mensajePregunta(contexto(ahora), limpia));
        return new RespuestaIA(texto, Origen.GEMINI, ahora);
    }

    // ---------- 3. Recomendaciones redactadas por la IA ----------

    @Override
    public RecomendacionesIA recomendaciones() {
        Guardado<RecomendacionesIA> guardado = recomendaciones.get();
        if (guardado != null && guardado.vigente(reloj.instant())) {
            return guardado.valor();
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        AnalisisRestoFlowAI analisis = restoFlowAI.analizar();
        RecomendacionesIA resultado;
        Duration vigencia = VIGENCIA_RECOMENDACIONES;
        try {
            String texto = llamar("recomendaciones", InstruccionesIA.sistemaAnalisis(),
                    InstruccionesIA.mensajeAnalisis(InstruccionesIA.contexto(ahora, analisis.ventas(),
                            analisis.insumos(), avisos.avisosPara(Rol.ADMIN, null).avisos())));
            List<Recomendacion> deIA = InstruccionesIA.leerRecomendaciones(texto);
            if (deIA.isEmpty()) {
                throw new AsistenteIANoDisponibleException("La IA respondió en un formato que no se pudo leer");
            }
            resultado = new RecomendacionesIA(deIA, Origen.GEMINI, ahora);
        } catch (AsistenteIANoDisponibleException e) {
            resultado = new RecomendacionesIA(analisis.recomendaciones(), Origen.REGLAS, ahora);
            vigencia = ESPERA_TRAS_FALLA;
        }
        recomendaciones.set(new Guardado<>("", resultado, reloj.instant().plus(vigencia)));
        return resultado;
    }

    // ---------- Apoyo ----------

    private String contexto(LocalDateTime ahora) {
        AnalisisRestoFlowAI analisis = restoFlowAI.analizar();
        return InstruccionesIA.contexto(ahora, analisis.ventas(), analisis.insumos(),
                avisos.avisosPara(Rol.ADMIN, null).avisos());
    }

    /** Llama a la IA, limpia la respuesta y deja constancia en la auditoría (sin el contenido). */
    private String llamar(String funcion, String instrucciones, String mensaje) {
        if (!ia.configurado()) {
            throw new AsistenteIANoDisponibleException(AsistenteIANoDisponibleException.SIN_CLAVE);
        }
        long inicio = System.nanoTime();
        try {
            String texto = InstruccionesIA.limpiar(ia.generar(instrucciones, mensaje));
            if (texto.isBlank()) {
                throw new AsistenteIANoDisponibleException(AsistenteIANoDisponibleException.NO_RESPONDE);
            }
            Auditoria.consultaIA(funcion, true, (System.nanoTime() - inicio) / 1_000_000);
            return texto;
        } catch (AsistenteIANoDisponibleException e) {
            Auditoria.consultaIA(funcion, false, (System.nanoTime() - inicio) / 1_000_000);
            throw e;
        }
    }

    private record Guardado<T>(String firma, T valor, Instant vence) {
        boolean vigente(Instant ahora) {
            return vence.isAfter(ahora);
        }
    }
}
