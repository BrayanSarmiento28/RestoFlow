package com.udec.restoflow.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logs del backend: cada petición deja una línea con su id, estado y duración; las operaciones importantes
 * quedan en la auditoría; y las contraseñas NUNCA aparecen en el log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LogsIntegracionTest {

    @Autowired
    private MockMvc mvc;

    private static String login(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    @DisplayName("El inicio de sesión queda en la auditoría y en el log de peticiones, sin la contraseña")
    void inicioDeSesionAuditado(CapturedOutput salida) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("admin@pruebas.com", "Admin2026*")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("[a-f0-9]{8}")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("admin@pruebas.com", "ClaveEquivocada1")))
                .andExpect(status().isUnauthorized());

        assertThat(salida.getOut())
                .contains("evento=INICIO_SESION resultado=OK usuario=admin@pruebas.com rol=ADMIN")
                .contains("evento=INICIO_SESION resultado=RECHAZADO usuario=admin@pruebas.com motivo=credenciales")
                .contains("POST /api/auth/login → 200")
                .contains("POST /api/auth/login → 401")
                .doesNotContain("Admin2026*")
                .doesNotContain("ClaveEquivocada1");
    }

    @Test
    @DisplayName("Se respeta el X-Request-Id que envía el cliente y las peticiones sin token quedan como anónimas")
    void idDePeticionYAnonimo(CapturedOutput salida) throws Exception {
        mvc.perform(get("/api/pedidos").header("X-Request-Id", "prueba-1234"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "prueba-1234"));

        assertThat(salida.getOut()).contains("[prueba-1234]").contains("GET /api/pedidos → 401")
                .contains("usuario=anónimo");
    }

    @Test
    @DisplayName("Un id de petición con caracteres raros se reemplaza (nadie puede inyectar texto en el log)")
    void idInvalidoSeReemplaza() throws Exception {
        mvc.perform(get("/api/pedidos").header("X-Request-Id", "x\nFALSO"))
                .andExpect(header().string("X-Request-Id", matchesPattern("[a-f0-9]{8}")));
    }

    @Test
    @DisplayName("El healthcheck de Docker no llena el log")
    void saludNoSeRegistra(CapturedOutput salida) throws Exception {
        mvc.perform(get("/api/salud")).andExpect(status().isOk());
        assertThat(salida.getOut()).doesNotContain("GET /api/salud →");
    }
}
