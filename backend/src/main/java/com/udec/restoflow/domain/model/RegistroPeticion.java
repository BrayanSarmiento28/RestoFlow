package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.time.LocalDateTime;

/**
 * Registro de actividad · una petición al API guardada en la base de datos (recomendación del comité:
 * "implementar logs que se guarden en la base de datos").
 * <p>
 * Guarda <b>qué</b> se pidió, <b>quién</b>, <b>cuándo</b>, <b>cómo terminó</b> y <b>cuánto tardó</b>. Nunca guarda el
 * cuerpo de la petición (contraseñas, códigos) ni el token.
 *
 * @param requestId  el mismo id que aparece en los logs de texto, para cruzar las dos fuentes
 * @param usuario    correo del usuario autenticado (null si fue anónimo, por ejemplo un login fallido)
 * @param rol        rol del usuario (null si fue anónimo)
 * @param ruta       ruta con su consulta, por ejemplo /api/pedidos?mesa=4 (se recorta a 500 caracteres)
 * @param estado     código HTTP de la respuesta (200, 404, 500...)
 */
public record RegistroPeticion(Long id, LocalDateTime fecha, String requestId, String usuario, String rol,
                               String metodo, String ruta, int estado, long duracionMs) {

    public static final int RUTA_MAXIMA = 500;
    /** Más de 1 segundo se considera una petición lenta (el mismo umbral del log de texto). */
    public static final long UMBRAL_LENTA_MS = 1000;

    public RegistroPeticion {
        if (fecha == null || metodo == null || metodo.isBlank() || ruta == null || ruta.isBlank()) {
            throw new DomainException("Un registro de actividad necesita fecha, método y ruta");
        }
        if (estado < 100 || estado > 599) {
            throw new DomainException("Código HTTP inválido: " + estado);
        }
        ruta = ruta.length() > RUTA_MAXIMA ? ruta.substring(0, RUTA_MAXIMA) : ruta;
        duracionMs = Math.max(0, duracionMs);
    }

    /** Un registro nuevo (aún sin id). */
    public static RegistroPeticion nuevo(LocalDateTime fecha, String requestId, String usuario, String rol,
                                         String metodo, String ruta, int estado, long duracionMs) {
        return new RegistroPeticion(null, fecha, requestId, usuario, rol, metodo, ruta, estado, duracionMs);
    }

    /** 5xx: falló el servidor. */
    public boolean esError() {
        return estado >= 500;
    }

    /** 4xx: se rechazó la petición (datos inválidos, sin permiso, no existe...). */
    public boolean esRechazo() {
        return estado >= 400 && estado < 500;
    }

    public boolean esLenta() {
        return duracionMs > UMBRAL_LENTA_MS;
    }
}
