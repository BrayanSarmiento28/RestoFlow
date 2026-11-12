package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.time.LocalDateTime;

/**
 * Qué registros de actividad buscar. Cada filtro es opcional (null = sin filtrar por eso).
 *
 * @param desde   desde esta fecha (incluida)
 * @param hasta   hasta esta fecha (sin incluirla)
 * @param usuario parte del correo, sin importar mayúsculas
 * @param ruta    parte de la ruta, por ejemplo "pedidos"
 * @param tipo    TODAS, EXITOSAS (2xx y 3xx), RECHAZADAS (4xx), ERRORES (5xx) o LENTAS (más de 1 s)
 */
public record FiltroRegistro(LocalDateTime desde, LocalDateTime hasta, String usuario, String metodo, String ruta,
                             Tipo tipo) {

    public enum Tipo { TODAS, EXITOSAS, RECHAZADAS, ERRORES, LENTAS }

    public FiltroRegistro {
        if (desde != null && hasta != null && !desde.isBefore(hasta)) {
            throw new DomainException("La fecha inicial debe ser anterior a la final");
        }
        usuario = vacioANull(usuario);
        metodo = vacioANull(metodo) == null ? null : metodo.trim().toUpperCase();
        ruta = vacioANull(ruta);
        tipo = tipo == null ? Tipo.TODAS : tipo;
    }

    /** Estado HTTP mínimo que cumple el tipo (incluido). */
    public int estadoDesde() {
        return switch (tipo) {
            case RECHAZADAS -> 400;
            case ERRORES -> 500;
            default -> 100;
        };
    }

    /** Estado HTTP máximo que cumple el tipo (incluido). */
    public int estadoHasta() {
        return switch (tipo) {
            case EXITOSAS -> 399;
            case RECHAZADAS -> 499;
            default -> 599;
        };
    }

    /** Duración mínima: solo las lentas pasan de 1 segundo. */
    public long duracionMinima() {
        return tipo == Tipo.LENTAS ? RegistroPeticion.UMBRAL_LENTA_MS + 1 : 0;
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
