package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.out.AsistenteIAPort;
import com.udec.restoflow.application.port.out.CatalogoExternoPort;
import com.udec.restoflow.infrastructure.adapter.out.externo.GeminiAdapter;
import com.udec.restoflow.infrastructure.adapter.out.externo.OpenFoodFactsAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * APIs externas: crea los clientes HTTP (con tiempo máximo de espera) y los adaptadores de Open Food Facts y Gemini.
 * La dirección y el tiempo se configuran en {@code restoflow.apis-externas} (ver {@link RestoFlowProperties}) y se
 * pueden cambiar con variables de entorno sin tocar el código.
 */
@Configuration
public class ApisExternasConfig {

    @Bean
    public CatalogoExternoPort catalogoExternoPort(RestClient.Builder builder, RestoFlowProperties propiedades,
                                                   Clock reloj) {
        RestoFlowProperties.ApisExternas apis = propiedades.apisExternas();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(apis.tiempoEsperaSegundos()));
        fabrica.setReadTimeout(Duration.ofSeconds(apis.tiempoEsperaSegundos()));
        RestClient cliente = builder.clone()
                .baseUrl(apis.openFoodFacts().url())
                // Open Food Facts pide identificar la aplicación que consulta
                .defaultHeader(HttpHeaders.USER_AGENT, apis.openFoodFacts().userAgent())
                .requestFactory(fabrica)
                .build();
        return new OpenFoodFactsAdapter(cliente, reloj);
    }

    /**
     * IA externa (Gemini). La clave va en un encabezado del cliente, no en la URL. Gemini tiene su propio tiempo
     * máximo (15 s por modelo) porque redactar tarda más que buscar un código de barras.
     */
    @Bean
    public AsistenteIAPort asistenteIAPort(RestClient.Builder builder, RestoFlowProperties propiedades) {
        RestoFlowProperties.Gemini gemini = propiedades.apisExternas().gemini();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(propiedades.apisExternas().tiempoEsperaSegundos()));
        fabrica.setReadTimeout(Duration.ofSeconds(gemini.tiempoEsperaSegundos()));
        RestClient.Builder cliente = builder.clone().baseUrl(gemini.url()).requestFactory(fabrica);
        if (gemini.configurado()) {
            cliente.defaultHeader("x-goog-api-key", gemini.apiKey().trim());
        }
        return new GeminiAdapter(cliente.build(), gemini.modelos(), gemini.configurado());
    }
}
