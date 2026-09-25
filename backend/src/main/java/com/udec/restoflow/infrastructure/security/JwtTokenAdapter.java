package com.udec.restoflow.infrastructure.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udec.restoflow.application.port.out.GeneradorTokenPort;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador de salida: implementa GeneradorTokenPort con JSON Web Tokens (JWT) firmados con HMAC-SHA256.
 * <p>
 * Un JWT tiene tres partes separadas por puntos: <b>encabezado.contenido.firma</b>.
 * El contenido lleva quién es el usuario y cuándo vence; la firma garantiza que nadie lo modificó.
 * Se implementa con clases estándar de Java para que el equipo entienda exactamente cómo funciona.
 */
@Component
public class JwtTokenAdapter implements GeneradorTokenPort {

    private static final String ALGORITMO = "HmacSHA256";
    private static final Base64.Encoder CODIFICADOR = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODIFICADOR = Base64.getUrlDecoder();

    private final byte[] clave;
    private final long vigenciaSegundos;
    private final ObjectMapper json;

    public JwtTokenAdapter(@Value("${restoflow.jwt.secret}") String secreto,
                           @Value("${restoflow.jwt.expiracion-minutos:480}") long expiracionMinutos,
                           ObjectMapper json) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException("restoflow.jwt.secret debe tener al menos 32 caracteres");
        }
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
        this.vigenciaSegundos = expiracionMinutos * 60;
        this.json = json;
    }

    @Override
    public String generarToken(Usuario usuario) {
        long ahora = Instant.now().getEpochSecond();
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("sub", usuario.getEmail());
        contenido.put("uid", usuario.getId());
        contenido.put("nombre", usuario.getNombre());
        contenido.put("rol", usuario.getRol().name());
        contenido.put("iat", ahora);                    // emitido en
        contenido.put("exp", ahora + vigenciaSegundos); // vence en

        String encabezado = codificar(Map.of("alg", "HS256", "typ", "JWT"));
        String cuerpo = codificar(contenido);
        return encabezado + "." + cuerpo + "." + firmar(encabezado + "." + cuerpo);
    }

    @Override
    public long getVigenciaSegundos() {
        return vigenciaSegundos;
    }

    /** Devuelve el usuario del token solo si la firma es válida y no ha vencido. */
    public Optional<UsuarioAutenticado> validar(String token) {
        try {
            String[] partes = token.split("\\.");
            if (partes.length != 3) {
                return Optional.empty();
            }
            byte[] firmaEsperada = firmar(partes[0] + "." + partes[1]).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(firmaEsperada, partes[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty(); // alguien modificó el token
            }
            Map<String, Object> contenido = json.readValue(DECODIFICADOR.decode(partes[1]),
                    new TypeReference<Map<String, Object>>() { });
            if (Instant.now().getEpochSecond() >= ((Number) contenido.get("exp")).longValue()) {
                return Optional.empty(); // token vencido
            }
            return Optional.of(new UsuarioAutenticado(
                    ((Number) contenido.get("uid")).longValue(),
                    (String) contenido.get("sub"),
                    (String) contenido.get("nombre"),
                    Rol.valueOf((String) contenido.get("rol"))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String codificar(Map<String, Object> datos) {
        try {
            return CODIFICADOR.encodeToString(json.writeValueAsBytes(datos));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo generar el token", e);
        }
    }

    private String firmar(String contenido) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(clave, ALGORITMO));
            return CODIFICADOR.encodeToString(mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }
}
