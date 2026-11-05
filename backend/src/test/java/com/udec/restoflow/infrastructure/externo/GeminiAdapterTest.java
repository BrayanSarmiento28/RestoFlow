package com.udec.restoflow.infrastructure.externo;

import com.udec.restoflow.domain.exception.AsistenteIANoDisponibleException;
import com.udec.restoflow.infrastructure.adapter.out.externo.GeminiAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Adaptador de Gemini (IA externa) probado con un servidor HTTP simulado: la petición que se envía (sin la clave en
 * la URL), cómo se lee la respuesta y qué pasa cuando falla. Las pruebas nunca llaman a internet ni gastan cupo.
 */
class GeminiAdapterTest {

    private static final String URL = "https://generativelanguage.googleapis.com";
    private static final String MODELO = "gemini-3.5-flash-lite";
    private static final String ENDPOINT = URL + "/v1beta/models/" + MODELO + ":generateContent";

    private static final String RESPUESTA = """
            {"candidates":[{"content":{"role":"model","parts":[
               {"text":"pensando...","thought":true},
               {"text":"Atienda primero "},{"text":"la mesa 4."}]},
             "finishReason":"STOP"}],
             "usageMetadata":{"totalTokenCount":120}}""";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(URL)
            .defaultHeader("x-goog-api-key", "clave-de-prueba");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final GeminiAdapter adaptador = new GeminiAdapter(builder.build(), MODELO, true);

    @Test
    @DisplayName("Envía instrucciones y mensaje a Gemini con la clave en el encabezado, y une el texto de la respuesta")
    void responde() {
        servidor.expect(once(), requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", "clave-de-prueba"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.system_instruction.parts[0].text").value("Eres RestoFlow AI"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Avisos: mesa 4"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("low"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        String texto = adaptador.generar("Eres RestoFlow AI", "Avisos: mesa 4");

        servidor.verify();
        assertThat(texto).isEqualTo("Atienda primero la mesa 4.");           // sin el "razonamiento" interno
        assertThat(adaptador.configurado()).isTrue();
    }

    @Test
    @DisplayName("Si se acabó el cupo gratuito (429) o la clave es inválida (400), responde 503 sin romper nada")
    void fallaGemini() {
        servidor.expect(once(), requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> adaptador.generar("i", "m"))
                .isInstanceOf(AsistenteIANoDisponibleException.class)
                .hasMessage(AsistenteIANoDisponibleException.NO_RESPONDE);
    }

    @Test
    @DisplayName("Si el modelo principal está saturado (503), responde el de respaldo")
    void modeloDeRespaldo() {
        RestClient.Builder otro = RestClient.builder().baseUrl(URL);
        MockRestServiceServer servidor2 = MockRestServiceServer.bindTo(otro).build();
        GeminiAdapter conRespaldo = new GeminiAdapter(otro.build(), List.of(MODELO, "gemini-respaldo"), true);
        servidor2.expect(once(), requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor2.expect(once(), requestTo(URL + "/v1beta/models/gemini-respaldo:generateContent"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        assertThat(conRespaldo.generar("i", "m")).isEqualTo("Atienda primero la mesa 4.");
        servidor2.verify();
    }

    @Test
    @DisplayName("Si la respuesta llega cortada o en otro formato (tiempo agotado), también prueba el de respaldo")
    void respuestaCortada() {
        RestClient.Builder otro = RestClient.builder().baseUrl(URL);
        MockRestServiceServer servidor2 = MockRestServiceServer.bindTo(otro).build();
        GeminiAdapter conRespaldo = new GeminiAdapter(otro.build(), List.of(MODELO, "gemini-respaldo"), true);
        servidor2.expect(once(), requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":", MediaType.APPLICATION_OCTET_STREAM));
        servidor2.expect(once(), requestTo(URL + "/v1beta/models/gemini-respaldo:generateContent"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        assertThat(conRespaldo.generar("i", "m")).isEqualTo("Atienda primero la mesa 4.");
        servidor2.verify();
    }

    @Test
    @DisplayName("Un problema con la clave (403) no se reintenta con otro modelo: fallaría igual")
    void claveInvalidaNoReintenta() {
        RestClient.Builder otro = RestClient.builder().baseUrl(URL);
        MockRestServiceServer servidor2 = MockRestServiceServer.bindTo(otro).build();
        GeminiAdapter conRespaldo = new GeminiAdapter(otro.build(), List.of(MODELO, "gemini-respaldo"), true);
        servidor2.expect(once(), requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> conRespaldo.generar("i", "m")).isInstanceOf(AsistenteIANoDisponibleException.class);
        servidor2.verify();                                                   // solo una llamada
    }

    @Test
    @DisplayName("Una respuesta vacía (por ejemplo, bloqueada por los filtros de seguridad) también es un 503")
    void respuestaVacia() {
        servidor.expect(once(), requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"candidates\":[],\"promptFeedback\":{\"blockReason\":\"OTHER\"}}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adaptador.generar("i", "m")).isInstanceOf(AsistenteIANoDisponibleException.class);
    }

    @Test
    @DisplayName("Sin clave configurada no llama a internet")
    void sinClave() {
        RestClient.Builder otro = RestClient.builder().baseUrl(URL);
        MockRestServiceServer sinLlamadas = MockRestServiceServer.bindTo(otro).build();
        sinLlamadas.expect(never(), requestTo(ENDPOINT));
        GeminiAdapter sinConfigurar = new GeminiAdapter(otro.build(), MODELO, false);

        assertThat(sinConfigurar.configurado()).isFalse();
        assertThatThrownBy(() -> sinConfigurar.generar("i", "m"))
                .hasMessage(AsistenteIANoDisponibleException.SIN_CLAVE);
        sinLlamadas.verify();
    }
}
