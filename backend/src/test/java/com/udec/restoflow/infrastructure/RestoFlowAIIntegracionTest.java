package com.udec.restoflow.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * RestoFlow AI con el API completo (seguridad, caso de uso y JPA con H2): quién puede consultarlo y qué responde.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RestoFlowAIIntegracionTest {

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

    private void crearUsuario(String admin, String email, String rol) throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Usuario IA\",\"email\":\"" + email + "\",\"password\":\"Clave2026x\","
                                + "\"rol\":\"" + rol + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RF-09 a RF-11 · ADMIN e INVENTARIO consultan el análisis y generan alertas; MESERO no puede")
    void permisosYRespuesta() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");
        crearUsuario(admin, "inventario.ia@pruebas.com", "INVENTARIO");
        crearUsuario(admin, "mesero.ia@pruebas.com", "MESERO");
        String inventario = login("inventario.ia@pruebas.com", "Clave2026x");
        String mesero = login("mesero.ia@pruebas.com", "Clave2026x");

        mvc.perform(get("/api/restoflow-ai/analisis").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generadoEn").exists())
                .andExpect(jsonPath("$.ventas.diasConVentas").isNumber())
                .andExpect(jsonPath("$.ventas.ventasPorDia.length()").value(7))
                .andExpect(jsonPath("$.insumos").isArray())
                .andExpect(jsonPath("$.recomendaciones").isArray());

        mvc.perform(get("/api/restoflow-ai/analisis").header("Authorization", "Bearer " + inventario))
                .andExpect(status().isOk());
        mvc.perform(post("/api/restoflow-ai/alertas").header("Authorization", "Bearer " + inventario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creadas").isNumber())
                .andExpect(jsonPath("$.cerradas").isNumber())
                .andExpect(jsonPath("$.yaAvisados").isNumber());

        mvc.perform(get("/api/restoflow-ai/analisis").header("Authorization", "Bearer " + mesero))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/restoflow-ai/analisis"))
                .andExpect(status().isUnauthorized());
    }
}
