package com.udec.restoflow.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtTokenAdapter jwt;

    public JwtAuthenticationFilter(JwtTokenAdapter jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado != null && encabezado.startsWith(PREFIJO)) {
            jwt.validar(encabezado.substring(PREFIJO.length())).ifPresent(usuario -> {
                var permisos = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name()));
                var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, permisos);
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            });
        }
        chain.doFilter(request, response);
    }
}
