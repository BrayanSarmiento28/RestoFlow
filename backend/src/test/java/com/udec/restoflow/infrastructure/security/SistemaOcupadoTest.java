package com.udec.restoflow.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import com.udec.restoflow.infrastructure.adapter.in.web.GlobalExceptionHandler;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RES-59 · Cuando la base de datos está saturada, el API responde 503 "sistema ocupado" de inmediato, en vez de
 * dejar la petición esperando en una fila que el cliente ya abandonó.
 */
class SistemaOcupadoTest {

    private static final String CLAVE = "clave-de-pruebas-restoflow-con-mas-de-32-caracteres";
    private final JwtTokenAdapter jwt = new JwtTokenAdapter(CLAVE, 60, new ObjectMapper());

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static Usuario mesero() {
        Usuario u = Usuario.nuevo("Laura Mesera", "laura@restoflow.com", "hash", Rol.MESERO, LocalDateTime.now());
        return new Usuario(7L, u.getNombre(), u.getEmail(), u.getPasswordHash(), u.getRol(), true, u.getFechaCreacion());
    }

    @Test
    @DisplayName("Sin conexión libre a la base de datos: el controlador responde 503 con Retry-After")
    void controladorResponde503() {
        GlobalExceptionHandler manejador = new GlobalExceptionHandler(Clock.systemDefaultZone());

        ResponseEntity<ErrorResponse> respuesta = manejador.sistemaOcupado(
                new CannotCreateTransactionException("Connection is not available, request timed out after 5000ms"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().mensaje()).contains("ocupado");
    }

    @Test
    @DisplayName("Una consulta que pasa su tiempo máximo también se responde como 503, no como error 500")
    void consultaLentaResponde503() {
        GlobalExceptionHandler manejador = new GlobalExceptionHandler(Clock.systemDefaultZone());

        assertThat(manejador.sistemaOcupado(new QueryTimeoutException("The query has timed out"))
                .getStatusCode().value()).isEqualTo(503);
    }

    @Test
    @DisplayName("Si al revisar la cuenta del token la base de datos está saturada, el filtro responde 503 y no sigue")
    void filtroResponde503() throws Exception {
        UsuarioRepositoryPort usuarios = mock(UsuarioRepositoryPort.class);
        when(usuarios.buscarPorId(anyLong())).thenThrow(new CannotCreateTransactionException("sin conexión libre"));
        JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwt, usuarios);
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/productos");
        peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generarToken(mesero()));
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(peticion, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(503);
        assertThat(respuesta.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(respuesta.getContentAsString()).contains("ocupado");
        assertThat(cadena.getRequest()).as("la petición no llega al controlador").isNull();
    }

    @Test
    @DisplayName("Con la base de datos disponible el filtro sigue igual: autentica y deja pasar la petición")
    void filtroNormal() throws Exception {
        UsuarioRepositoryPort usuarios = mock(UsuarioRepositoryPort.class);
        when(usuarios.buscarPorId(7L)).thenReturn(Optional.of(mesero()));
        JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwt, usuarios);
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/productos");
        peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generarToken(mesero()));
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(peticion, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }
}
