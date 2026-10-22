package com.udec.restoflow.infrastructure.adapter.out.externo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.udec.restoflow.application.port.out.CatalogoExternoPort;
import com.udec.restoflow.domain.exception.ServicioExternoNoDisponibleException;
import com.udec.restoflow.domain.model.ProductoExterno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de salida hacia <a href="https://world.openfoodfacts.org">Open Food Facts</a>: base de datos abierta y
 * gratuita de productos de supermercado por código de barras, <b>sin clave</b> (no hay secretos que guardar).
 * <p>
 * Llamada: {@code GET /api/v2/product/{codigo}?fields=code,product_name,product_name_es,brands,quantity,image_front_small_url}
 * <p>
 * Reglas de uso del servicio: se envía un {@code User-Agent} que identifica a RestoFlow, y las respuestas se guardan
 * en memoria (24 h si se encontró el producto, 1 h si no) para no repetir consultas.
 * Si el servicio falla o tarda más del tiempo máximo, se lanza {@link ServicioExternoNoDisponibleException} (503).
 */
public class OpenFoodFactsAdapter implements CatalogoExternoPort {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsAdapter.class);
    static final String FUENTE = "Open Food Facts";
    static final String CAMPOS = "code,product_name,product_name_es,brands,quantity,image_front_small_url";
    private static final Duration VIGENCIA_ENCONTRADO = Duration.ofHours(24);
    private static final Duration VIGENCIA_NO_ENCONTRADO = Duration.ofHours(1);
    private static final int MAXIMO_EN_MEMORIA = 500;

    private final RestClient http;
    private final Clock reloj;
    private final Map<String, Guardado> cache = new ConcurrentHashMap<>();

    public OpenFoodFactsAdapter(RestClient http, Clock reloj) {
        this.http = http;
        this.reloj = reloj;
    }

    @Override
    public Optional<ProductoExterno> buscarPorCodigo(String codigo) {
        Instant ahora = reloj.instant();
        Guardado guardado = cache.get(codigo);
        if (guardado != null && guardado.vence().isAfter(ahora)) {
            log.debug("Producto {} tomado de la caché", codigo);
            return guardado.producto();
        }
        Optional<ProductoExterno> producto = consultar(codigo);
        if (cache.size() >= MAXIMO_EN_MEMORIA) {
            cache.clear();                      // memoria acotada: se vuelve a llenar con el uso
        }
        cache.put(codigo, new Guardado(producto,
                ahora.plus(producto.isPresent() ? VIGENCIA_ENCONTRADO : VIGENCIA_NO_ENCONTRADO)));
        return producto;
    }

    private Optional<ProductoExterno> consultar(String codigo) {
        long inicio = System.nanoTime();
        try {
            Respuesta respuesta = http.get()
                    .uri(uri -> uri.path("/api/v2/product/{codigo}").queryParam("fields", CAMPOS).build(codigo))
                    .retrieve()
                    .body(Respuesta.class);
            Optional<ProductoExterno> producto = convertir(codigo, respuesta);
            log.info("Open Food Facts · código {} → {} en {} ms", codigo,
                    producto.isPresent() ? "encontrado" : "no existe", (System.nanoTime() - inicio) / 1_000_000);
            return producto;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {     // la v2 responde 404 si el producto no existe
                log.info("Open Food Facts · código {} → no existe", codigo);
                return Optional.empty();
            }
            log.warn("Open Food Facts respondió {} para el código {}", e.getStatusCode(), codigo);
            throw new ServicioExternoNoDisponibleException(FUENTE);
        } catch (RestClientException e) {
            log.warn("No se pudo consultar Open Food Facts: {}", e.getMessage());
            throw new ServicioExternoNoDisponibleException(FUENTE);
        }
    }

    /** status 1 = encontrado; status 0 = no existe. Se prefiere el nombre en español. */
    static Optional<ProductoExterno> convertir(String codigo, Respuesta respuesta) {
        if (respuesta == null || respuesta.status() != 1 || respuesta.producto() == null) {
            return Optional.empty();
        }
        Producto p = respuesta.producto();
        String nombre = vacioANull(p.nombreEs()) != null ? p.nombreEs().trim() : vacioANull(p.nombre());
        return Optional.of(new ProductoExterno(codigo, nombre, vacioANull(p.marcas()), vacioANull(p.cantidad()),
                vacioANull(p.imagen()), FUENTE));
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private record Guardado(Optional<ProductoExterno> producto, Instant vence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(@JsonProperty("status") int status, @JsonProperty("product") Producto producto) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Producto(@JsonProperty("product_name") String nombre,
                    @JsonProperty("product_name_es") String nombreEs,
                    @JsonProperty("brands") String marcas,
                    @JsonProperty("quantity") String cantidad,
                    @JsonProperty("image_front_small_url") String imagen) {
    }
}
