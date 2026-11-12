package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.domain.exception.CodigoRecuperacionInvalidoException;
import com.udec.restoflow.domain.exception.CorreoYaRegistradoException;
import com.udec.restoflow.domain.exception.CredencialesInvalidasException;
import com.udec.restoflow.domain.exception.CuentaInactivaException;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.exception.ProductoExternoNoEncontradoException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.exception.ServicioExternoNoDisponibleException;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
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
    /** Segundos que el cliente debe esperar antes de reintentar cuando el sistema está ocupado (503). */
    static final int SEGUNDOS_PARA_REINTENTAR = 5;

    /** Reloj de la aplicación (zona horaria de Colombia en Docker); en las pruebas se puede fijar. */
    private final Clock reloj;

    public GlobalExceptionHandler(Clock reloj) {
        this.reloj = reloj;
    }

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

    /** La regla existe pero el registro no está en el estado que se necesita (ej. pedido ya en preparación). */
    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<ErrorResponse> operacionNoPermitida(OperacionNoPermitidaException e) {
        return respuesta(HttpStatus.CONFLICT, e.getMessage(), null);              // 409
    }

    /** El catálogo externo (Open Food Facts) no tiene ese código de barras. */
    @ExceptionHandler(ProductoExternoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> productoExternoNoEncontrado(ProductoExternoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage(), null);             // 404
    }

    /** Un servicio de terceros no respondió (Open Food Facts, Gmail...): no es culpa del usuario. */
    @ExceptionHandler(ServicioExternoNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> servicioExterno(ServicioExternoNoDisponibleException e) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), null);   // 503
    }

    /**
     * RES-59 · El servidor está saturado: no hubo una conexión libre a la base de datos en el tiempo máximo
     * (spring.datasource.hikari.connection-timeout) o la consulta pasó su tiempo máximo
     * (spring.transaction.default-timeout). Se responde 503 de inmediato, en vez de dejar la petición en una
     * fila que nadie espera, y se le dice al cliente cuándo reintentar.
     */
    @ExceptionHandler({CannotCreateTransactionException.class, QueryTimeoutException.class,
            TransactionTimedOutException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ErrorResponse> sistemaOcupado(Exception e) {
        log.warn("Sistema ocupado, se responde 503: {}", e.getMessage());
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(SEGUNDOS_PARA_REINTENTAR))
                .body(new ErrorResponse(LocalDateTime.now(reloj), status.value(), status.getReasonPhrase(),
                        "El sistema está ocupado en este momento. Intente de nuevo en unos segundos.", null));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage(), null);             // 404
    }

    @ExceptionHandler({CodigoRecuperacionInvalidoException.class, DomainException.class})
    public ResponseEntity<ErrorResponse> reglaDeNegocio(DomainException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage(), null);           // 400
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
                .body(new ErrorResponse(LocalDateTime.now(reloj), status.value(), status.getReasonPhrase(), mensaje, campos));
    }
}
