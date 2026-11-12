package com.udec.restoflow.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro de actividad de punta a punta: una petición real pasa por el filtro, se guarda en la tabla
 * registro_peticiones (H2) y el ADMIN la consulta por el API. Los demás roles no pueden verla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RegistroActividadIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String login(String email, String password) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    /**
     * El guardado es asíncrono (no frena la respuesta), así que se consulta el registro hasta 5 s, hasta que
     * aparezca la petición con ese id. La consulta va sin caracteres especiales: MockMvc codifica la URL
     * (un "%3D" escrito a mano llegaría como "%253D").
     */
    private JsonNode esperarRegistros(String admin, String consulta, String requestId) {
        return await("la petición " + requestId + " en el registro")
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> consultarRegistro(admin, consulta), resultado -> fila(resultado, requestId) != null);
    }

    private JsonNode consultarRegistro(String admin, String consulta) throws Exception {
        String cuerpo = mvc.perform(get("/api/registro?" + consulta).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    /** La fila con ese id de petición (puede haber otras peticiones en la misma página), o null. */
    private static JsonNode fila(JsonNode resultado, String requestId) {
        for (JsonNode fila : resultado.at("/pagina/registros")) {
            if (requestId.equals(fila.get("requestId").asText())) {
                return fila;
            }
        }
        return null;
    }

    @Test
    @DisplayName("Cada petición queda guardada con usuario, rol, método, ruta, estado y duración")
    void peticionGuardada() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");

        mvc.perform(get("/api/productos?soloActivos=true").header("Authorization", "Bearer " + admin)
                        .header("X-Request-Id", "registro-bd-0001"))
                .andExpect(status().isOk());

        JsonNode r = esperarRegistros(admin, "ruta=soloActivos", "registro-bd-0001");
        JsonNode fila = fila(r, "registro-bd-0001");
        assertEquals("registro-bd-0001", fila.get("requestId").asText());
        assertEquals("admin@pruebas.com", fila.get("usuario").asText());
        assertEquals("ADMIN", fila.get("rol").asText());
        assertEquals("GET", fila.get("metodo").asText());
        assertEquals("/api/productos?soloActivos=true", fila.get("ruta").asText());
        assertEquals(200, fila.get("estado").asInt());
        assertTrue(fila.get("duracionMs").asLong() >= 0);
        assertTrue(r.at("/resumen/total").asLong() >= 1);
    }

    @Test
    @DisplayName("Los rechazos también se guardan: un login fallido queda como anónimo y con estado 401")
    void rechazoGuardado() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "registro-bd-0401")
                        .content("{\"email\":\"admin@pruebas.com\",\"password\":\"ClaveEquivocada1\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode r = esperarRegistros(admin, "tipo=RECHAZADAS&metodo=post&ruta=auth", "registro-bd-0401");
        JsonNode fila = fila(r, "registro-bd-0401");
        assertEquals(401, fila.get("estado").asInt());
        assertTrue(fila.get("usuario").isNull());
        assertTrue(r.at("/resumen/rechazadas").asLong() >= 1);
    }

    @Test
    @DisplayName("Solo el ADMIN consulta el registro; un MESERO recibe 403 y sin token 401")
    void permisos() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mesero Registro\",\"email\":\"mesero.registro@pruebas.com\","
                                + "\"password\":\"Clave2026x\",\"rol\":\"MESERO\"}"))
                .andExpect(status().isCreated());
        String mesero = login("mesero.registro@pruebas.com", "Clave2026x");

        mvc.perform(get("/api/registro").header("Authorization", "Bearer " + mesero)).andExpect(status().isForbidden());
        mvc.perform(get("/api/registro")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/registro?desde=2026-11-05&hasta=2026-11-01").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }
}
