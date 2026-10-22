package com.udec.restoflow.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Registro de auditoría: deja constancia de <b>quién hizo qué y cuándo</b> en las operaciones importantes
 * del negocio (iniciar sesión, crear cuentas, pedidos, ventas, inventario y alertas).
 * <p>
 * Todas las líneas salen por el logger {@code restoflow.auditoria}. En {@code logback-spring.xml} ese logger
 * se escribe en la consola y, además, en su propio archivo {@code logs/auditoria.log}, separado de los
 * mensajes técnicos. Cada línea tiene el formato {@code evento=... clave=valor}, así se puede buscar con
 * {@code Select-String} o {@code grep} sin herramientas especiales.
 * <p>
 * Regla de seguridad: aquí <b>nunca</b> se escriben contraseñas, tokens ni códigos de recuperación.
 * <p>
 * Se usa SLF4J, que es solo una fachada (interfaz) de logs: la capa de aplicación no depende de cómo ni dónde
 * se guardan; eso lo decide la infraestructura (Logback).
 */
final class Auditoria {

    private static final Logger log = LoggerFactory.getLogger("restoflow.auditoria");

    private Auditoria() {
    }

    // ---------- Cuentas y acceso (HU-01, HU-02, HU-03) ----------

    static void inicioSesion(String email, String rol) {
        log.info("evento=INICIO_SESION resultado=OK usuario={} rol={}", email, rol);
    }

    static void inicioSesionFallido(String email, String motivo) {
        log.warn("evento=INICIO_SESION resultado=RECHAZADO usuario={} motivo={}", limpio(email), motivo);
    }

    static void cuentaCreada(String email, String rol) {
        log.info("evento=CUENTA_CREADA usuario={} rol={}", email, rol);
    }

    static void recuperacionSolicitada(String email, boolean cuentaExiste) {
        // Se registra si la cuenta existía (útil para soporte) aunque al usuario siempre se le responda igual
        log.info("evento=RECUPERACION_SOLICITADA usuario={} cuentaExiste={}", limpio(email), cuentaExiste);
    }

    static void contrasenaRestablecida(String email) {
        log.info("evento=CONTRASENA_RESTABLECIDA usuario={}", email);
    }

    static void codigoRecuperacionInvalido(String email) {
        log.warn("evento=CODIGO_RECUPERACION resultado=RECHAZADO usuario={}", limpio(email));
    }

    // ---------- Pedidos y ventas (RF-02) ----------

    static void pedidoRegistrado(Long pedidoId, int mesa, Long meseroId, BigDecimal total) {
        log.info("evento=PEDIDO_REGISTRADO pedido={} mesa={} mesero={} total={}", pedidoId, mesa, meseroId, total);
    }

    static void pedidoCancelado(Long pedidoId) {
        log.info("evento=PEDIDO_CANCELADO pedido={}", pedidoId);
    }

    static void ventaRegistrada(Long pedidoId, int mesa, BigDecimal total) {
        log.info("evento=VENTA_REGISTRADA pedido={} mesa={} total={}", pedidoId, mesa, total);
    }

    // ---------- Inventario y alertas (RF-05 a RF-07) ----------

    static void movimientoInventario(String tipo, String ingrediente, BigDecimal cantidad, BigDecimal stockFinal,
                                     Long usuarioId) {
        log.info("evento=MOVIMIENTO_INVENTARIO tipo={} ingrediente=\"{}\" cantidad={} stockFinal={} usuario={}",
                tipo, ingrediente, ControlAlertas.numero(cantidad), ControlAlertas.numero(stockFinal),
                usuarioId == null ? "sistema" : usuarioId);
    }

    static void faltanteEnVenta(Long pedidoId, String ingrediente, BigDecimal faltante) {
        log.warn("evento=FALTANTE_INVENTARIO pedido={} ingrediente=\"{}\" faltante={}", pedidoId, ingrediente,
                ControlAlertas.numero(faltante));
    }

    static void alertaCreada(String ingrediente, String prioridad) {
        log.warn("evento=ALERTA_CREADA ingrediente=\"{}\" prioridad={}", ingrediente, prioridad);
    }

    static void alertaCerrada(String ingrediente, int cantidad) {
        log.info("evento=ALERTA_CERRADA ingrediente=\"{}\" alertas={}", ingrediente, cantidad);
    }

    // ---------- Catálogo externo (Open Food Facts) ----------

    static void productoExternoConsultado(String codigo, String fuente) {
        log.info("evento=PRODUCTO_EXTERNO_CONSULTADO codigo={} fuente=\"{}\"", codigo, fuente);
    }

    /** Quita saltos de línea de lo que escribe el usuario, para que nadie pueda inventar líneas falsas en el log. */
    private static String limpio(String texto) {
        if (texto == null) {
            return "(vacío)";
        }
        String sinSaltos = texto.replaceAll("[\\r\\n\\t]", "_").trim();
        return sinSaltos.length() > 120 ? sinSaltos.substring(0, 120) + "…" : sinSaltos;
    }
}
