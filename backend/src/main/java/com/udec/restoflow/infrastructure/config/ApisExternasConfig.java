package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.out.CatalogoExternoPort;
import com.udec.restoflow.infrastructure.adapter.out.externo.OpenFoodFactsAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * APIs externas: crea el cliente HTTP (con tiempo máximo de espera y User-Agent) y el adaptador de Open Food Facts.
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
}
