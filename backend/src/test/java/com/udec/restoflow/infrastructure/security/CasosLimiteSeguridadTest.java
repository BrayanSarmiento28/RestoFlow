package com.udec.restoflow.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udec.restoflow.infrastructure.config.RestoFlowProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Casos límite de los tokens y de la configuración de Gemini. */
class CasosLimiteSeguridadTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("El backend no arranca con una clave de tokens de menos de 32 caracteres")
    void claveCorta() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenAdapter("corta", 60, json));
        assertThrows(IllegalStateException.class, () -> new JwtTokenAdapter(null, 60, json));
    }

    @Test
    @DisplayName("Un token vacío o ilegible se rechaza sin errores")
    void tokenIlegible() {
        JwtTokenAdapter jwt = new JwtTokenAdapter("clave-de-pruebas-restoflow-con-mas-de-32-caracteres", 60, json);

        assertThat(jwt.validar(null)).isEmpty();
        assertThat(jwt.validar("uno.dos")).isEmpty();
    }

    @Test
    @DisplayName("Gemini: sin clave no está configurado y la clave nunca aparece en el log")
    void gemini() {
        RestoFlowProperties.Gemini sinClave = new RestoFlowProperties.Gemini(" ", "gemini-a", null, "https://g", 15);
        RestoFlowProperties.Gemini conClave = new RestoFlowProperties.Gemini("AIza-secreta", " gemini-a ",
                Arrays.asList("gemini-b", null, " ", "gemini-a"), "https://g", 15);

        assertThat(sinClave.configurado()).isFalse();
        assertThat(new RestoFlowProperties.Gemini(null, "gemini-a", null, "https://g", 15).configurado()).isFalse();
        assertThat(sinClave.modelos()).containsExactly("gemini-a");
        assertThat(sinClave.toString()).contains("(sin clave)");

        assertThat(conClave.configurado()).isTrue();
        assertThat(conClave.modelos()).isEqualTo(List.of("gemini-a", "gemini-b"));   // sin vacíos ni repetidos
        assertThat(conClave.toString()).contains("****").doesNotContain("AIza-secreta");
    }
}
