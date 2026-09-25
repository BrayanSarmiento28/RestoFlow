package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.domain.exception.CodigoRecuperacionInvalidoException;
import com.udec.restoflow.domain.exception.CorreoYaRegistradoException;
import com.udec.restoflow.domain.exception.CredencialesInvalidasException;
import com.udec.restoflow.domain.exception.CuentaInactivaException;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.ErrorResponse;
import com.udec.restoflow.infrastructure.adapter.out.mail.CorreoNoEnviadoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce los errores (del dominio o técnicos) a respuestas HTTP con un formato único,
 * para que el frontend siempre sepa qué mostrar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credenciales(CredencialesInvalidasException e) {
        return respuesta(HttpStatus.UNAUTHORIZED, e.getMessage(), null);          // 401
    }

    @ExceptionHandler(CuentaInactivaException.class)
    public ResponseEntity<ErrorResponse> cuentaInactiva(CuentaInactivaException e) {
        return respuesta(HttpStatus.FORBIDDEN, e.getMessage(), null);             // 403
    }

    @ExceptionHandler(CorreoYaRegistradoException.class)
    public ResponseEntity<ErrorResponse> correoRepetido(CorreoYaRegistradoException e) {
        return respuesta(HttpStatus.CONFLICT, e.getMessage(), null);              // 409
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage(), null);             // 404
    }

    @ExceptionHandler({CodigoRecuperacionInvalidoException.class, DomainException.class})
    public ResponseEntity<ErrorResponse> reglaDeNegocio(DomainException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage(), null);           // 400
    }

    @ExceptionHandler(CorreoNoEnviadoException.class)
    public ResponseEntity<ErrorResponse> correoNoEnviado(CorreoNoEnviadoException e) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), null);   // 503
    }

    /** Errores de validación de los DTO (@NotBlank, @Email...): se indica qué campo falló. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return respuesta(HttpStatus.BAD_REQUEST, "Hay datos inválidos en la solicitud", campos);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> formatoInvalido(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "La solicitud no tiene el formato esperado", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "La ruta solicitada no existe", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido en esta ruta", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception e) {
        log.error("Error no controlado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", null);
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String mensaje, Map<String, String> campos) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), mensaje, campos));
    }
}
