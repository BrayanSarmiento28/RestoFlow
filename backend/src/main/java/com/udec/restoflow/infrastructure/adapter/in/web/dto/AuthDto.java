package com.udec.restoflow.infrastructure.adapter.in.web.dto;

import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO (Data Transfer Object): la forma exacta del JSON que entra y sale del API.
 * Se separan del dominio para que un cambio en el JSON no obligue a cambiar las reglas del negocio.
 */
public final class AuthDto {

    private AuthDto() {
    }

    // ---------- Entrada ----------

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) { }

    public record RecuperarRequest(
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") String email) { }

    public record RestablecerRequest(
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") String email,
            @NotBlank(message = "El código es obligatorio")
            @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos") String codigo,
            @NotBlank(message = "La nueva contraseña es obligatoria") String nuevaPassword) { }

    public record CrearUsuarioRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String nombre,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") String email,
            @NotBlank(message = "La contraseña es obligatoria") @Size(max = 72) String password,
            @NotNull(message = "El rol es obligatorio") Rol rol) { }

    // ---------- Salida ----------

    public record LoginResponse(String token, long expiraEnSegundos, UsuarioResponse usuario) { }

    public record MensajeResponse(String mensaje) { }

    /** Datos públicos de un usuario. Nunca incluye la contraseña ni su hash. */
    public record UsuarioResponse(Long id, String nombre, String email, Rol rol, boolean activo) {
        public static UsuarioResponse desde(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), u.isActivo());
        }
    }
}
