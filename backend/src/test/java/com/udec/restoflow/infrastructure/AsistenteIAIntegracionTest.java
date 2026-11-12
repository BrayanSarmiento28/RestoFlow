package com.udec.restoflow.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udec.restoflow.domain.exception.AsistenteIANoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IA externa de punta a punta, en las pruebas SIN clave de Gemini: se comprueba el plan B (la campana y las
 * recomendaciones siguen funcionando con reglas), los permisos y que el chat responde 503 con un mensaje claro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AsistenteIAIntegracionTest {

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

    @Test
    @DisplayName("Sin Gemini: la campana y las recomendaciones usan reglas; el chat responde 503 y es solo del admin")
    void planBYPermisos() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Inventario IA\",\"email\":\"inventario.gemini@pruebas.com\","
                                + "\"password\":\"Clave2026x\",\"rol\":\"INVENTARIO\"}"))
                .andExpect(status().isCreated());
        String inventario = login("inventario.gemini@pruebas.com", "Clave2026x");

        mvc.perform(get("/api/avisos/resumen").header("Authorization", "Bearer " + inventario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").isNotEmpty())
                .andExpect(jsonPath("$.origen").value("REGLAS"));

        mvc.perform(get("/api/restoflow-ai/recomendaciones-ia").header("Authorization", "Bearer " + inventario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("REGLAS"))
                .andExpect(jsonPath("$.recomendaciones").isArray());

        String pregunta = "{\"pregunta\":\"¿Qué debo comprar esta semana?\"}";
        mvc.perform(post("/api/restoflow-ai/preguntar").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(pregunta))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(AsistenteIANoDisponibleException.SIN_CLAVE));
        mvc.perform(post("/api/restoflow-ai/preguntar").header("Authorization", "Bearer " + inventario)
                        .contentType(MediaType.APPLICATION_JSON).content(pregunta))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/restoflow-ai/preguntar").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pregunta\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
