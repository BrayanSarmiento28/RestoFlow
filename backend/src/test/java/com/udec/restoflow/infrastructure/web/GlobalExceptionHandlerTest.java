package com.udec.restoflow.infrastructure.web;

import com.udec.restoflow.domain.exception.CorreoYaRegistradoException;
import com.udec.restoflow.domain.exception.ProductoExternoNoEncontradoException;
import com.udec.restoflow.infrastructure.adapter.in.web.GlobalExceptionHandler;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada error del API llega al frontend con el mismo formato y el código HTTP que le corresponde. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler manejador = new GlobalExceptionHandler(
            Clock.fixed(Instant.parse("2026-11-12T17:00:00Z"), ZoneId.of("America/Bogota")));

    private static void verificar(ResponseEntity<ErrorResponse> r, int status, String mensaje) {
        assertThat(r.getStatusCode().value()).isEqualTo(status);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().status()).isEqualTo(status);
        assertThat(r.getBody().mensaje()).isEqualTo(mensaje);
    }

    @Test
    @DisplayName("409 · el correo ya está registrado")
    void correoRepetido() {
        CorreoYaRegistradoException e = new CorreoYaRegistradoException("laura@restoflow.com");
        verificar(manejador.correoRepetido(e), 409, e.getMessage());
    }

    @Test
    @DisplayName("404 · el código de barras no está en el catálogo externo")
    void productoExterno() {
        ProductoExternoNoEncontradoException e = new ProductoExternoNoEncontradoException("7702011012345");
        verificar(manejador.productoExternoNoEncontrado(e), 404, e.getMessage());
    }

    @Test
    @DisplayName("404 · ruta que no existe y 405 · método que la ruta no admite")
    void rutaYMetodo() {
        verificar(manejador.rutaInexistente(new NoResourceFoundException(HttpMethod.GET, "/api/no-existe")),
                404, "La ruta solicitada no existe");
        verificar(manejador.metodoNoPermitido(new HttpRequestMethodNotSupportedException("DELETE")),
                405, "Método HTTP no permitido en esta ruta");
    }

    @Test
    @DisplayName("500 · un error inesperado no muestra detalles internos al usuario")
    void inesperado() {
        verificar(manejador.inesperado(new IllegalStateException("NullPointer en la línea 42")),
                500, "Ocurrió un error inesperado");
    }
}
