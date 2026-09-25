package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.CodigoRecuperacionInvalidoException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Código de 6 dígitos que se envía al correo para restablecer la contraseña (HU-02).
 * <p>
 * Reglas del negocio:
 * <ul>
 *   <li>Vence a los {@value #VIGENCIA_MINUTOS} minutos.</li>
 *   <li>Solo se puede usar una vez.</li>
 *   <li>Se guarda cifrado (hash), igual que una contraseña.</li>
 * </ul>
 */
public class CodigoRecuperacion {

    public static final int VIGENCIA_MINUTOS = 15;
    public static final int LONGITUD_CODIGO = 6;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final Long id;
    private final Long usuarioId;
    private final String codigoHash;
    private final LocalDateTime fechaExpiracion;
    private boolean usado;

    /** Reconstruye un código existente (lo usa la capa de persistencia). */
    public CodigoRecuperacion(Long id, Long usuarioId, String codigoHash, LocalDateTime fechaExpiracion, boolean usado) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.codigoHash = codigoHash;
        this.fechaExpiracion = fechaExpiracion;
        this.usado = usado;
    }

    /** Crea un código nuevo para un usuario, con vencimiento a los 15 minutos. */
    public static CodigoRecuperacion nuevo(Long usuarioId, String codigoHash, LocalDateTime ahora) {
        return new CodigoRecuperacion(null, usuarioId, codigoHash, ahora.plusMinutes(VIGENCIA_MINUTOS), false);
    }

    /** Genera el código en texto plano (ej. "048213") que se enviará por correo. */
    public static String generarCodigoPlano() {
        int numero = ALEATORIO.nextInt((int) Math.pow(10, LONGITUD_CODIGO));
        return String.format("%0" + LONGITUD_CODIGO + "d", numero);
    }

    public boolean estaVigente(LocalDateTime ahora) {
        return !usado && ahora.isBefore(fechaExpiracion);
    }

    /** Marca el código como utilizado. Falla si ya se usó o si venció. */
    public void usar(LocalDateTime ahora) {
        if (!estaVigente(ahora)) {
            throw new CodigoRecuperacionInvalidoException();
        }
        this.usado = true;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getCodigoHash() { return codigoHash; }
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public boolean isUsado() { return usado; }
}
