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

/**
 * Mi cuenta (cambiar la contraseña) y desactivar o reactivar cuentas del personal, por el API completo
 * (seguridad, caso de uso y JPA).
 */
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

    private ResultActions estado(String token, Object id, String cuerpo) throws Exception {
        return mvc.perform(patch("/api/usuarios/" + id + "/estado").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("El administrador desactiva una cuenta: su sesión abierta deja de servir y no puede volver a entrar")
    void desactivarYReactivar() throws Exception {
        String admin = token("admin@pruebas.com", "Admin2026*");
        String creada = mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mesero Turno\",\"email\":\"mesero.turno@pruebas.com\","
                                + "\"password\":\"Mesero2026\",\"rol\":\"MESERO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(creada).get("id").asLong();
        String mesero = token("mesero.turno@pruebas.com", "Mesero2026");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + mesero)).andExpect(status().isOk());

        // Solo el administrador puede hacerlo, y el cuerpo es obligatorio
        estado(mesero, id, "{\"activa\":false}").andExpect(status().isForbidden());
        estado(admin, id, "{}").andExpect(status().isBadRequest());
        estado(admin, 999_999, "{\"activa\":false}").andExpect(status().isNotFound());

        estado(admin, id, "{\"activa\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // El token que ya tenía deja de funcionar de inmediato y no puede iniciar sesión de nuevo
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + mesero)).andExpect(status().isUnauthorized());
        // Al iniciar sesión, la contraseña es correcta pero la cuenta está desactivada: 403 con un mensaje claro
        login("mesero.turno@pruebas.com", "Mesero2026")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("La cuenta está desactivada. Comuníquese con el administrador"));

        // Sigue en la lista (no se borra) y al reactivarla vuelve a entrar con la misma contraseña
        mvc.perform(get("/api/usuarios/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        estado(admin, id, "{\"activa\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
        login("mesero.turno@pruebas.com", "Mesero2026").andExpect(status().isOk());
    }

    @Test
    @DisplayName("El administrador no puede desactivar su propia cuenta (409)")
    void noDesactivarseASiMismo() throws Exception {
        String admin = token("admin@pruebas.com", "Admin2026*");
        String yo = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString();

        estado(admin, json.readTree(yo).get("id").asLong(), "{\"activa\":false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("No puedes desactivar tu propia cuenta"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
    }
}
