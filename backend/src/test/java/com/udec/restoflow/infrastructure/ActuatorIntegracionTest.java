package com.udec.restoflow.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Factor IX (desechabilidad): Docker y cualquier orquestador saben si el proceso está vivo y listo
 * gracias a Actuator. Solo se publican la salud y la información de la versión.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("health, liveness y readiness son públicos y responden UP")
    void salud() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("No se publican detalles internos de la salud (base de datos, disco)")
    void sinDetalles() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("info muestra el nombre de la aplicación")
    void info() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isOk()).andExpect(jsonPath("$.app.nombre").value("RestoFlow"));
    }

    @Test
    @DisplayName("Los demás endpoints de Actuator (env, beans, configprops) no existen o piden sesión")
    void restoCerrado() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/beans")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/configprops")).andExpect(status().is4xxClientError());
    }
}
