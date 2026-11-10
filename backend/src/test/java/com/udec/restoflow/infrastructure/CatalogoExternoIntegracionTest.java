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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/inventario/catalogo-externo/{codigo} con el API completo (seguridad + caso de uso + adaptador).
 * En las pruebas Open Food Facts apunta a una dirección que no responde: así se comprueba el 503.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogoExternoIntegracionTest {

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
    @DisplayName("Solo inventario y admin; código inválido 400; servicio caído 503 con mensaje claro")
    void catalogoExterno() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Mesero Catalogo\",\"email\":\"mesero.cat@pruebas.com\",\"password\":\"Mesero2026\",\"rol\":\"MESERO\"}"))
                .andExpect(status().isCreated());
        String mesero = login("mesero.cat@pruebas.com", "Mesero2026");

        mvc.perform(get("/api/inventario/catalogo-externo/7702011012345")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inventario/catalogo-externo/7702011012345").header("Authorization", "Bearer " + mesero))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/inventario/catalogo-externo/12AB").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("8, 12, 13 o 14 números")));

        mvc.perform(get("/api/inventario/catalogo-externo/7702011012345").header("Authorization", "Bearer " + admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje", containsString("Open Food Facts no está disponible")));
    }
}
