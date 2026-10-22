package com.udec.restoflow.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Log de cada petición HTTP que llega al API (factor XI de los 12 factores: logs como flujo de eventos).
 * <ol>
 *   <li>Le asigna un <b>id de petición</b> (o reutiliza el que mande el cliente en {@code X-Request-Id}) y lo
 *       devuelve en la respuesta. Ese id aparece en <b>todas</b> las líneas de log que genere la petición,
 *       así se pueden seguir juntas aunque lleguen muchas al tiempo.</li>
 *   <li>Al terminar escribe una línea: método, ruta, estado HTTP, duración y usuario.
 *       Errores 5xx → ERROR, 4xx → WARN, más de 1 segundo → WARN (petición lenta), lo demás → INFO.</li>
 * </ol>
 * Corre <b>antes</b> que Spring Security (máxima prioridad) para registrar también los 401 y 403.
 * No registra el healthcheck de Docker (/api/salud y /actuator) ni Swagger, que solo harían ruido.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegistroPeticionesFilter extends OncePerRequestFilter {

    public static final String ENCABEZADO = "X-Request-Id";
    /** El filtro del token guarda aquí el correo del usuario autenticado. */
    public static final String ATRIBUTO_USUARIO = "restoflow.usuario";
    static final String MDC_PETICION = "requestId";
    static final String MDC_USUARIO = "usuario";
    static final long UMBRAL_LENTA_MS = 1000;

    private static final Logger log = LoggerFactory.getLogger("restoflow.peticiones");
    /** Solo se acepta un id de petición corto y sin caracteres raros (evita inyectar texto en el log). */
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod())
                || ruta.equals("/api/salud")
                || ruta.startsWith("/actuator")
                || ruta.startsWith("/swagger-ui")
                || ruta.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = idDePeticion(request.getHeader(ENCABEZADO));
        MDC.put(MDC_PETICION, requestId);
        response.setHeader(ENCABEZADO, requestId);
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            registrar(request, response.getStatus(), ms);
            MDC.remove(MDC_PETICION);
            MDC.remove(MDC_USUARIO);
        }
    }

    private void registrar(HttpServletRequest request, int estado, long ms) {
        Object usuario = request.getAttribute(ATRIBUTO_USUARIO);
        String ruta = request.getQueryString() == null ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
        String linea = "{} {} → {} en {} ms · usuario={}";
        Object[] datos = {request.getMethod(), ruta, estado, ms, usuario == null ? "anónimo" : usuario};
        if (estado >= 500) {
            log.error(linea, datos);
        } else if (estado >= 400) {
            log.warn(linea, datos);
        } else if (ms > UMBRAL_LENTA_MS) {
            log.warn(linea + " (petición lenta)", datos);
        } else {
            log.info(linea, datos);
        }
    }

    private static String idDePeticion(String recibido) {
        if (recibido != null && ID_VALIDO.matcher(recibido).matches()) {
            return recibido;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
