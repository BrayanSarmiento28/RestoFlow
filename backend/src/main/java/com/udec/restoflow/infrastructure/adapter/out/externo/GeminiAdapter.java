package com.udec.restoflow.infrastructure.adapter.out.externo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.udec.restoflow.application.port.out.AsistenteIAPort;
import com.udec.restoflow.domain.exception.AsistenteIANoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adaptador de salida hacia la IA de Google, <a href="https://ai.google.dev">Gemini API</a> (plan gratuito de
 * Google AI Studio).
 * <p>
 * Llamada: {@code POST /v1beta/models/{modelo}:generateContent} con la clave en el encabezado
 * {@code x-goog-api-key} (nunca en la URL, para que no quede en ningún log). La clave viene de la variable de
 * entorno {@code GEMINI_API_KEY}; si no está, {@link #configurado()} es false y RestoFlow usa su plan B sin IA.
 * <p>
 * Si Gemini falla, tarda más del tiempo máximo o responde vacío, se lanza
 * {@link AsistenteIANoDisponibleException} (503). Si el modelo principal está saturado se prueba uno de respaldo
 * (GEMINI_MODELOS_RESPALDO). En el log queda cuánto tardó, nunca lo que se preguntó.
 */
public class GeminiAdapter implements AsistenteIAPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiAdapter.class);

    private final RestClient http;
    private final List<String> modelos;
    private final boolean configurado;

    /**
     * @param http        cliente ya apuntando a la URL de Gemini, con la clave y el tiempo máximo de espera
     * @param modelos     el modelo principal y, después, los de respaldo (se prueban en ese orden)
     * @param configurado false si no hay clave
     */
    public GeminiAdapter(RestClient http, List<String> modelos, boolean configurado) {
        if (modelos == null || modelos.isEmpty()) {
            throw new IllegalArgumentException("Debe haber al menos un modelo de Gemini");
        }
        this.http = http;
        this.modelos = List.copyOf(modelos);
        this.configurado = configurado;
    }

    public GeminiAdapter(RestClient http, String modelo, boolean configurado) {
        this(http, List.of(modelo), configurado);
    }

    @Override
    public boolean configurado() {
        return configurado;
    }

    /**
     * Prueba el modelo principal y, si falla por algo que otro modelo sí puede resolver (saturado, sin cupo, retirado,
     * demasiado lento o una respuesta cortada), el siguiente de respaldo. Los errores de la clave (400, 401, 403) no
     * se reintentan: con otro modelo fallarían igual.
     */
    @Override
    public String generar(String instrucciones, String mensaje) {
        if (!configurado) {
            throw new AsistenteIANoDisponibleException(AsistenteIANoDisponibleException.SIN_CLAVE);
        }
        AsistenteIANoDisponibleException ultimo = null;
        for (String modelo : modelos) {
            try {
                return generarCon(modelo, instrucciones, mensaje);
            } catch (AsistenteIANoDisponibleException e) {
                ultimo = e;
                if (!otroModeloPuedeAyudar(e.getCause())) {
                    throw e;
                }
            }
        }
        // Sin modelos configurados no hubo ningún intento: se avisa igual que cuando Gemini no responde
        throw ultimo != null ? ultimo : new AsistenteIANoDisponibleException(AsistenteIANoDisponibleException.NO_RESPONDE);
    }

    /**
     * Sí: 404 (modelo retirado), 429 (cupo de ese modelo), 5xx (saturado), tiempo agotado o respuesta incompleta.
     * No: 400, 401 y 403 (la clave o la petición), que fallarían igual con cualquier modelo.
     */
    static boolean otroModeloPuedeAyudar(Throwable causa) {
        if (causa instanceof RestClientResponseException r) {
            return valeLaPenaOtro(r.getStatusCode().value());
        }
        return causa instanceof RestClientException;     // tiempo agotado, conexión cortada, respuesta incompleta
    }

    static boolean valeLaPenaOtro(int estado) {
        return estado == 404 || estado == 429 || estado >= 500;
    }

    private String generarCon(String modelo, String instrucciones, String mensaje) {
        long inicio = System.nanoTime();
        try {
            Respuesta respuesta = http.post()
                    .uri("/v1beta/models/{modelo}:generateContent", modelo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Peticion.de(instrucciones, mensaje))
                    .retrieve()
                    .body(Respuesta.class);
            String texto = texto(respuesta);
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            if (texto.isBlank()) {
                log.warn("Gemini ({}) respondió sin texto en {} ms", modelo, ms);
                throw new AsistenteIANoDisponibleException(AsistenteIANoDisponibleException.NO_RESPONDE);
            }
            log.info("Gemini ({}) respondió en {} ms · {} caracteres", modelo, ms, texto.length());
            return texto;
        } catch (RestClientResponseException e) {
            // 429 = se acabó el cupo gratuito, 503 = modelo saturado, 400/403 = problema con la clave
            log.warn("Gemini ({}) respondió {} en {} ms", modelo, e.getStatusCode().value(),
                    (System.nanoTime() - inicio) / 1_000_000);
            throw new AsistenteIANoDisponibleException(AsistenteIANoDisponibleException.NO_RESPONDE, e);
        } catch (RestClientException e) {
            log.warn("No se pudo consultar Gemini ({}): {}", modelo, e.getMessage());
            throw new AsistenteIANoDisponibleException(AsistenteIANoDisponibleException.NO_RESPONDE, e);
        }
    }

    /** Une el texto de la primera respuesta, sin las partes de "razonamiento" interno del modelo. */
    static String texto(Respuesta respuesta) {
        if (respuesta == null || respuesta.candidatos() == null || respuesta.candidatos().isEmpty()) {
            return "";
        }
        Contenido contenido = respuesta.candidatos().get(0).contenido();
        if (contenido == null || contenido.partes() == null) {
            return "";
        }
        return contenido.partes().stream()
                .filter(p -> !Boolean.TRUE.equals(p.pensamiento()))
                .map(Parte::texto)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                .trim();
    }

    // ---------- JSON que se envía ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Peticion(@JsonProperty("system_instruction") Contenido instrucciones,
                    @JsonProperty("contents") List<Contenido> contenidos,
                    @JsonProperty("generationConfig") Configuracion configuracion) {

        static Peticion de(String instrucciones, String mensaje) {
            return new Peticion(new Contenido(null, List.of(new Parte(instrucciones, null))),
                    List.of(new Contenido("user", List.of(new Parte(mensaje, null)))),
                    // Poco "razonamiento": respuestas cortas y rápidas
                    new Configuracion(new Razonamiento("low")));
        }
    }

    record Configuracion(@JsonProperty("thinkingConfig") Razonamiento razonamiento) {
    }

    record Razonamiento(@JsonProperty("thinkingLevel") String nivel) {
    }

    // ---------- JSON que se envía y se recibe ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Contenido(@JsonProperty("role") String rol, @JsonProperty("parts") List<Parte> partes) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Parte(@JsonProperty("text") String texto, @JsonProperty("thought") Boolean pensamiento) {
    }

    // ---------- JSON que se recibe ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(@JsonProperty("candidates") List<Candidato> candidatos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidato(@JsonProperty("content") Contenido contenido) {
    }
}
