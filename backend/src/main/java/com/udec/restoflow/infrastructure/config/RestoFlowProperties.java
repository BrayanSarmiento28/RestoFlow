package com.udec.restoflow.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * <b>Factor III de los 12 factores (configuración en el entorno), implementado en el código.</b>
 * <p>
 * Toda la configuración propia de RestoFlow ({@code restoflow.*} en application.yml) se lee en esta única clase,
 * con nombres y tipos claros. Cada valor puede venir de una variable de entorno (JWT_SECRET, CORS_ORIGINS...).
 * <p>
 * Además se <b>valida al arrancar</b> (@Validated): si una variable falta o tiene un valor imposible (una clave JWT
 * corta, un correo mal escrito, un modo de correo desconocido), la aplicación <b>no arranca</b> y el log dice
 * exactamente qué está mal. Es mejor fallar al desplegar que descubrir el error con el restaurante abierto.
 */
@Validated
@ConfigurationProperties(prefix = "restoflow")
public record RestoFlowProperties(
        /* desarrollo · pre-produccion · produccion (se muestra en /api/salud y en Swagger) */
        @DefaultValue("desarrollo") @NotBlank String entorno,
        /* Carga el menú y el inventario de ejemplo si las tablas están vacías */
        @DefaultValue("false") boolean datosDemo,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull @DefaultValue Correo correo,
        @Valid @NotNull AdminInicial adminInicial,
        @Valid @NotNull @DefaultValue Logs logs,
        @Valid @NotNull @DefaultValue ApisExternas apisExternas) {

    /** Tokens de sesión. Variable de entorno: JWT_SECRET. */
    public record Jwt(
            @NotBlank @Size(min = 32, message = "debe tener al menos 32 caracteres (variable JWT_SECRET)") String secret,
            @DefaultValue("480") @Min(5) @Max(1440) long expiracionMinutos) {

        @Override
        public String toString() {
            return "Jwt[secret=****, expiracionMinutos=" + expiracionMinutos + "]";   // nunca mostrar la clave
        }
    }

    /** Direcciones del frontend que pueden llamar al API. Variable: CORS_ORIGINS. */
    public record Cors(@NotEmpty List<String> origenesPermitidos) {
    }

    /** "consola" (pruebas: el código sale en el log) o "gmail" (correo real). Variable: CORREO_MODO. */
    public record Correo(
            @DefaultValue("consola") @Pattern(regexp = "consola|gmail", message = "debe ser consola o gmail") String modo,
            @DefaultValue("RestoFlow") String remitenteNombre) {
    }

    /** Administrador que se crea en el primer arranque. Variables: ADMIN_EMAIL, ADMIN_PASSWORD. */
    public record AdminInicial(
            @DefaultValue("Administrador RestoFlow") String nombre,
            @NotBlank @Email String email,
            @NotBlank String contrasena) {

        @Override
        public String toString() {
            return "AdminInicial[nombre=" + nombre + ", email=" + email + ", contrasena=****]";
        }
    }

    /** Carpeta de los archivos de log. Variable: LOG_DIR. */
    public record Logs(@DefaultValue("logs") String directorio) {
    }

    /** Servicios de terceros (factor IV). */
    public record ApisExternas(
            @DefaultValue("4") @Min(1) @Max(30) int tiempoEsperaSegundos,
            @Valid @NotNull @DefaultValue OpenFoodFacts openFoodFacts) {
    }

    /** Variables: OPENFOODFACTS_URL. */
    public record OpenFoodFacts(
            @DefaultValue("https://world.openfoodfacts.org") @NotBlank String url,
            @DefaultValue("RestoFlow/0.3 (Proyecto Integrador UdeC)") @NotBlank String userAgent) {
    }
}
