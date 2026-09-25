package com.udec.restoflow.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/** Formato único de los errores del API, acordado con el frontend en docs/sprint-1.md. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(LocalDateTime fecha, int status, String error, String mensaje, Map<String, String> campos) {
}
