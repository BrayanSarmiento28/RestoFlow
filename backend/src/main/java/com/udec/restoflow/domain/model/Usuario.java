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
    /** Caracteres permitidos antes y después de la @ (clases simples: se evalúan en tiempo lineal). */
    private static final Pattern PARTE_LOCAL = Pattern.compile("[\\w.+-]++");
    private static final Pattern DOMINIO = Pattern.compile("[\\w.-]++");

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
        boolean tieneLetra = contrasenaPlana.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
        boolean tieneNumero = contrasenaPlana.chars().anyMatch(c -> c >= '0' && c <= '9');
        if (!tieneLetra || !tieneNumero) {
            throw new DomainException("La contraseña debe contener al menos una letra y un número");
        }
    }

    /** Todos los correos se guardan en minúsculas y sin espacios para evitar duplicados. */
    public static String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new DomainException("El correo es obligatorio");
        }
        String normalizado = email.trim().toLowerCase();
        if (!formatoEmailValido(normalizado)) {
            throw new DomainException("El correo " + email + " no tiene un formato válido");
        }
        return normalizado;
    }

    // ---------- Validaciones internas ----------

    /**
     * nombre@dominio.ext: una sola @, el dominio con al menos un punto y sin partes vacías.
     * Se revisa por partes en vez de con una sola expresión regular con repeticiones anidadas, que con un texto
     * malicioso puede tardar muchísimo (ReDoS) o desbordar la pila (reglas java:S5998 y java:S8786 de SonarQube).
     */
    private static boolean formatoEmailValido(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 0 || arroba != email.lastIndexOf('@')) {
            return false;
        }
        String local = email.substring(0, arroba);
        String dominio = email.substring(arroba + 1);
        return PARTE_LOCAL.matcher(local).matches() && DOMINIO.matcher(dominio).matches()
                && dominio.contains(".") && !dominio.startsWith(".") && !dominio.endsWith(".") && !dominio.contains("..");
    }

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
