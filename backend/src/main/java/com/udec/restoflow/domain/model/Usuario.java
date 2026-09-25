package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.CuentaInactivaException;
import com.udec.restoflow.domain.exception.DomainException;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Empleado del restaurante que usa el sistema.
 * <p>
 * Guarda la contraseña solo como "hash" (cifrada). El dominio nunca conoce la contraseña
 * en texto plano después de crear la cuenta; el cifrado lo hace un puerto de salida.
 */
public class Usuario {

    public static final int LONGITUD_MINIMA_CONTRASENA = 8;
    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final Long id;
    private String nombre;
    private final String email;
    private String passwordHash;
    private Rol rol;
    private boolean activo;
    private final LocalDateTime fechaCreacion;

    /** Reconstruye un usuario existente (lo usa la capa de persistencia al leer de la base de datos). */
    public Usuario(Long id, String nombre, String email, String passwordHash, Rol rol, boolean activo,
                   LocalDateTime fechaCreacion) {
        this.id = id;
        this.nombre = validarNombre(nombre);
        this.email = normalizarEmail(email);
        this.passwordHash = requerido(passwordHash, "La contraseña");
        this.rol = rolRequerido(rol);
        this.activo = activo;
        this.fechaCreacion = fechaCreacion;
    }

    /** Crea una cuenta nueva, activa, lista para guardarse (HU-03). */
    public static Usuario nuevo(String nombre, String email, String passwordHash, Rol rol, LocalDateTime ahora) {
        return new Usuario(null, nombre, email, passwordHash, rol, true, ahora);
    }

    // ---------- Reglas de negocio ----------

    /** HU-01: una cuenta desactivada no puede iniciar sesión. */
    public void validarPuedeIngresar() {
        if (!activo) {
            throw new CuentaInactivaException();
        }
    }

    /** HU-02: reemplaza la contraseña por una nueva (ya cifrada). */
    public void cambiarContrasena(String nuevoPasswordHash) {
        this.passwordHash = requerido(nuevoPasswordHash, "La nueva contraseña");
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    /**
     * Política de contraseñas del restaurante: mínimo 8 caracteres, con al menos una letra y un número.
     * Se valida ANTES de cifrarla, porque después ya no se puede leer.
     */
    public static void validarContrasenaSegura(String contrasenaPlana) {
        if (contrasenaPlana == null || contrasenaPlana.length() < LONGITUD_MINIMA_CONTRASENA) {
            throw new DomainException("La contraseña debe tener al menos " + LONGITUD_MINIMA_CONTRASENA + " caracteres");
        }
        if (!contrasenaPlana.matches(".*[A-Za-z].*") || !contrasenaPlana.matches(".*\\d.*")) {
            throw new DomainException("La contraseña debe contener al menos una letra y un número");
        }
    }

    /** Todos los correos se guardan en minúsculas y sin espacios para evitar duplicados. */
    public static String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new DomainException("El correo es obligatorio");
        }
        String normalizado = email.trim().toLowerCase();
        if (!FORMATO_EMAIL.matcher(normalizado).matches()) {
            throw new DomainException("El correo " + email + " no tiene un formato válido");
        }
        return normalizado;
    }

    // ---------- Validaciones internas ----------

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.trim().length() < 3) {
            throw new DomainException("El nombre debe tener al menos 3 caracteres");
        }
        return nombre.trim();
    }

    private static Rol rolRequerido(Rol rol) {
        if (rol == null) {
            throw new DomainException("El rol es obligatorio");
        }
        return rol;
    }

    private static String requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(campo + " es obligatoria");
        }
        return valor;
    }

    // ---------- Consultas ----------

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
