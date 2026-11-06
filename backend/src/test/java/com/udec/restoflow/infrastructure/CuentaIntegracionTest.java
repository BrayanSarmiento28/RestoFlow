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
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mi cuenta · Cambiar la contraseña con la sesión iniciada, por el API completo (seguridad, caso de uso y JPA). */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CuentaIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String token(String email, String password) throws Exception {
        String respuesta = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    private ResultActions cambiar(String token, String cuerpo) throws Exception {
        return mvc.perform(patch("/api/auth/contrasena").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("Mi cuenta: cada usuario ve sus datos y cambia su contraseña escribiendo la actual")
    void cambiarContrasena() throws Exception {
        String admin = token("admin@pruebas.com", "Admin2026*");
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Cocina Cuenta\",\"email\":\"cocina.cuenta@pruebas.com\","
                                + "\"password\":\"Cocina2026\",\"rol\":\"COCINA\"}"))
                .andExpect(status().isCreated());
        String cocina = token("cocina.cuenta@pruebas.com", "Cocina2026");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + cocina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cocina Cuenta"))
                .andExpect(jsonPath("$.rol").value("COCINA"));

        // Sin sesión no se puede; con la actual equivocada responde 400 (la sesión sigue abierta)
        mvc.perform(patch("/api/auth/contrasena").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actual\":\"Cocina2026\",\"nueva\":\"Fogon2027\"}")).andExpect(status().isUnauthorized());
        cambiar(cocina, "{\"actual\":\"Otra2026\",\"nueva\":\"Fogon2027\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La contraseña actual no es correcta"));
        cambiar(cocina, "{\"actual\":\"Cocina2026\",\"nueva\":\"corta\"}").andExpect(status().isBadRequest());
        cambiar(cocina, "{\"actual\":\"\",\"nueva\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.actual").exists());

        cambiar(cocina, "{\"actual\":\"Cocina2026\",\"nueva\":\"Fogon2027\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Tu contraseña se cambió correctamente."));

        // La anterior ya no sirve; la nueva sí
        login("cocina.cuenta@pruebas.com", "Cocina2026").andExpect(status().isUnauthorized());
        login("cocina.cuenta@pruebas.com", "Fogon2027").andExpect(status().isOk());
    }
}
