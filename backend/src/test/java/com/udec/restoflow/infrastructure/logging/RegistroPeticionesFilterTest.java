package com.udec.restoflow.infrastructure.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Filtro de logs de peticiones probado solo (sin Spring): niveles, id de petición y limpieza del contexto. */
@ExtendWith(OutputCaptureExtension.class)
class RegistroPeticionesFilterTest {

    private final RegistroPeticionesFilter filtro = new RegistroPeticionesFilter();

    private MockHttpServletResponse ejecutar(MockHttpServletRequest peticion, FilterChain cadena) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    @Test
    @DisplayName("Durante la petición el id está en el contexto del log; al terminar se limpia")
    void contexto() throws Exception {
        AtomicReference<String> visto = new AtomicReference<>();
        MockHttpServletResponse r = ejecutar(new MockHttpServletRequest("GET", "/api/pedidos"),
                (req, res) -> visto.set(MDC.get("requestId")));

        assertThat(visto.get()).matches("[a-f0-9]{8}");
        assertThat(r.getHeader("X-Request-Id")).isEqualTo(visto.get());
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("usuario")).isNull();
    }

    @Test
    @DisplayName("Una respuesta 500 queda como ERROR con el usuario que hizo la petición")
    void error500(CapturedOutput salida) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/pedidos");
        peticion.setAttribute(RegistroPeticionesFilter.ATRIBUTO_USUARIO, "laura@restoflow.com");
        peticion.setQueryString("mesa=4");

        ejecutar(peticion, (req, res) -> ((MockHttpServletResponse) res).setStatus(500));

        assertThat(salida.getOut()).contains("ERROR").contains("POST /api/pedidos?mesa=4 → 500")
                .contains("usuario=laura@restoflow.com");
    }

    @Test
    @DisplayName("Una petición de más de 1 segundo queda como WARN (petición lenta)")
    void lenta(CapturedOutput salida) throws Exception {
        ejecutar(new MockHttpServletRequest("GET", "/api/panel/resumen"), (req, res) -> {
            try {
                Thread.sleep(RegistroPeticionesFilter.UMBRAL_LENTA_MS + 50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(salida.getOut()).contains("WARN").contains("(petición lenta)");
    }

    @Test
    @DisplayName("OPTIONS, el healthcheck, Actuator y Swagger no se registran")
    void excluidas() throws Exception {
        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("OPTIONS", "/api/pedidos"))).isTrue();
        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("GET", "/api/salud"))).isTrue();
        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health/readiness"))).isTrue();
        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html"))).isTrue();
        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("GET", "/api/pedidos"))).isFalse();
    }
}
