package com.udec.restoflow.infrastructure.externo;

import com.udec.restoflow.domain.exception.ServicioExternoNoDisponibleException;
import com.udec.restoflow.domain.model.ProductoExterno;
import com.udec.restoflow.infrastructure.adapter.out.externo.OpenFoodFactsAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Adaptador de Open Food Facts probado con un servidor HTTP simulado (MockRestServiceServer):
 * la petición que se envía, cómo se lee el JSON, la caché y qué pasa cuando el servicio falla.
 */
class OpenFoodFactsAdapterTest {

    private static final String URL = "https://world.openfoodfacts.org";
    private static final String CODIGO = "7702011012345";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Bogota"));

    private static final String JSON_ENCONTRADO = """
            {"code":"7702011012345","status":1,"status_verbose":"product found",
             "product":{"product_name":"White rice","product_name_es":"Arroz blanco","brands":"Diana",
                        "quantity":"1 kg","image_front_small_url":"https://images.openfoodfacts.org/arroz.jpg",
                        "nutriscore_grade":"a"}}""";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(URL);
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final OpenFoodFactsAdapter adaptador = new OpenFoodFactsAdapter(builder.build(), RELOJ);

    @Test
    @DisplayName("Pide solo los campos necesarios y prefiere el nombre en español")
    void encontrado() {
        servidor.expect(once(), requestTo(startsWith(URL + "/api/v2/product/" + CODIGO)))
                .andExpect(method(GET))
                .andExpect(queryParam("fields", containsString("product_name_es")))
                .andRespond(withSuccess(JSON_ENCONTRADO, MediaType.APPLICATION_JSON));

        Optional<ProductoExterno> producto = adaptador.buscarPorCodigo(CODIGO);

        servidor.verify();
        assertThat(producto).contains(new ProductoExterno(CODIGO, "Arroz blanco", "Diana", "1 kg",
                "https://images.openfoodfacts.org/arroz.jpg", "Open Food Facts"));
    }

    @Test
    @DisplayName("Una segunda consulta del mismo código sale de la caché (no vuelve a llamar)")
    void cache() {
        servidor.expect(once(), requestTo(startsWith(URL + "/api/v2/product/" + CODIGO)))
                .andRespond(withSuccess(JSON_ENCONTRADO, MediaType.APPLICATION_JSON));

        adaptador.buscarPorCodigo(CODIGO);
        adaptador.buscarPorCodigo(CODIGO);

        servidor.verify();
    }

    @Test
    @DisplayName("Sin nombre en español usa el nombre general; los campos vacíos quedan en null")
    void sinNombreEnEspanol() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withSuccess("""
                        {"status":1,"product":{"product_name":"Panela","product_name_es":"","brands":" ","quantity":null}}""",
                        MediaType.APPLICATION_JSON));

        ProductoExterno producto = adaptador.buscarPorCodigo(CODIGO).orElseThrow();

        assertThat(producto.nombre()).isEqualTo("Panela");
        assertThat(producto.marca()).isNull();
        assertThat(producto.cantidad()).isNull();
    }

    @Test
    @DisplayName("status 0 (producto inexistente) → vacío")
    void statusCero() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withSuccess("{\"code\":\"7702011012345\",\"status\":0,\"status_verbose\":\"product not found\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(adaptador.buscarPorCodigo(CODIGO)).isEmpty();
    }

    @Test
    @DisplayName("Error 404 (así responde la v2 cuando no existe) → vacío, y queda en caché")
    void noExiste404() {
        servidor.expect(once(), requestTo(startsWith(URL))).andRespond(withResourceNotFound());

        assertThat(adaptador.buscarPorCodigo(CODIGO)).isEmpty();
        assertThat(adaptador.buscarPorCodigo(CODIGO)).isEmpty();
        servidor.verify();
    }

    @Test
    @DisplayName("Error 500 → servicio no disponible (503 para el usuario), y no se guarda en caché")
    void errorDelServidor() {
        servidor.expect(ExpectedCount.twice(), requestTo(startsWith(URL))).andRespond(withServerError());

        assertThatThrownBy(() -> adaptador.buscarPorCodigo(CODIGO)).isInstanceOf(ServicioExternoNoDisponibleException.class);
        assertThatThrownBy(() -> adaptador.buscarPorCodigo(CODIGO)).isInstanceOf(ServicioExternoNoDisponibleException.class);
        servidor.verify();
    }

    @Test
    @DisplayName("Demasiadas consultas (429) → servicio no disponible")
    void limiteDeConsultas() {
        servidor.expect(once(), requestTo(startsWith(URL))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> adaptador.buscarPorCodigo(CODIGO))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasMessageContaining("Open Food Facts");
    }
}
