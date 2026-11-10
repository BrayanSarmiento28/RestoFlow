package com.udec.restoflow.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.model.Usuario;
import com.udec.restoflow.infrastructure.logging.RegistroPeticionesFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Se ejecuta en cada petición: lee el encabezado "Authorization: Bearer &lt;token&gt;",
 * valida el token y, si es correcto, indica a Spring Security quién es el usuario y su rol.
 * <p>
 * Además revisa que la cuenta siga activa: si el administrador la desactivó, el token deja de servir de
 * inmediato (el API responde 401 y el frontend cierra la sesión), sin esperar las 8 horas de vigencia.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtTokenAdapter jwt;
    private final UsuarioRepositoryPort usuarios;

    public JwtAuthenticationFilter(JwtTokenAdapter jwt, UsuarioRepositoryPort usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado != null && encabezado.startsWith(PREFIJO)) {
            jwt.validar(encabezado.substring(PREFIJO.length())).filter(this::cuentaActiva).ifPresent(usuario -> {
                var permisos = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name()));
                var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, permisos);
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
                // Para los logs: cada línea de esta petición mostrará quién la hizo (nunca el token)
                MDC.put("usuario", usuario.email());
                request.setAttribute(RegistroPeticionesFilter.ATRIBUTO_USUARIO, usuario.email());
                request.setAttribute(RegistroPeticionesFilter.ATRIBUTO_ROL, usuario.rol().name());
            });
        }
        chain.doFilter(request, response);
    }

    /** Consulta por llave primaria (muy rápida): la cuenta existe y no fue desactivada. */
    private boolean cuentaActiva(UsuarioAutenticado usuario) {
        return usuarios.buscarPorId(usuario.id()).map(Usuario::isActivo).orElse(false);
    }
}
