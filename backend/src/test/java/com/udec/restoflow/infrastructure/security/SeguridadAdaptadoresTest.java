package com.udec.restoflow.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-01 · Adaptadores de seguridad sin levantar Spring: tokens JWT y cifrado BCrypt. */
class SeguridadAdaptadoresTest {

    private static final String CLAVE = "clave-de-pruebas-restoflow-con-mas-de-32-caracteres";
    private final ObjectMapper json = new ObjectMapper();
    private final JwtTokenAdapter jwt = new JwtTokenAdapter(CLAVE, 60, json);

    private static Usuario cocinero() {
        Usuario u = Usuario.nuevo("Pedro Cocina", "pedro@restoflow.com", "hash", Rol.COCINA, LocalDateTime.now());
        return new Usuario(5L, u.getNombre(), u.getEmail(), u.getPasswordHash(), u.getRol(), true, u.getFechaCreacion());
    }

    @Test
    @DisplayName("El token generado se valida y devuelve quién es el usuario y su rol")
    void tokenValido() {
        String token = jwt.generarToken(cocinero());

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwt.validar(token)).hasValueSatisfying(u -> {
            assertThat(u.id()).isEqualTo(5L);
            assertThat(u.email()).isEqualTo("pedro@restoflow.com");
            assertThat(u.rol()).isEqualTo(Rol.COCINA);
        });
        assertThat(jwt.getVigenciaSegundos()).isEqualTo(3600);
    }

    @Test
    @DisplayName("Si alguien cambia el contenido (por ejemplo el rol a ADMIN) la firma ya no coincide")
    void tokenAlterado() {
        String[] partes = jwt.generarToken(cocinero()).split("\\.");
        String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("COCINA", "ADMIN");
        String falso = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(contenido.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];

        assertThat(jwt.validar(falso)).isEmpty();
    }

    @Test
    @DisplayName("Un token firmado con otra clave, mal formado o vencido no sirve")
    void tokensInvalidos() {
        String deOtroServidor = new JwtTokenAdapter("otra-clave-distinta-de-mas-de-32-caracteres!!", 60, json)
                .generarToken(cocinero());
        assertThat(jwt.validar(deOtroServidor)).isEmpty();
        assertThat(jwt.validar("no-es-un-token")).isEmpty();
        assertThat(jwt.validar("a.b.c")).isEmpty();
        assertThat(new JwtTokenAdapter(CLAVE, 0, json).validar(new JwtTokenAdapter(CLAVE, 0, json)
                .generarToken(cocinero()))).isEmpty();
    }

    @Test
    @DisplayName("La clave del JWT debe tener al menos 32 caracteres")
    void claveCorta() {
        assertThatThrownBy(() -> new JwtTokenAdapter("corta", 60, json)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("BCrypt: el hash no es la contraseña, cambia cada vez y aun así se verifica")
    void bcrypt() {
        BCryptCifradoAdapter cifrado = new BCryptCifradoAdapter();
        String hash1 = cifrado.cifrar("Mesero2026");
        String hash2 = cifrado.cifrar("Mesero2026");

        assertThat(hash1).isNotEqualTo("Mesero2026").startsWith("$2").isNotEqualTo(hash2);
        assertThat(cifrado.coincide("Mesero2026", hash1)).isTrue();
        assertThat(cifrado.coincide("mesero2026", hash1)).isFalse();
        assertThat(cifrado.coincide(null, hash1)).isFalse();
        assertThat(cifrado.coincide("Mesero2026", null)).isFalse();
    }
}
