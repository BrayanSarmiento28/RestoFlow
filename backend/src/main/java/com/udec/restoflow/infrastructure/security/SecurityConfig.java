package com.udec.restoflow.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * RF-01 · Control de acceso basado en roles (RBAC).
 * Aquí se define qué rutas del API son públicas y cuáles exigen un rol.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain reglasDeSeguridad(HttpSecurity http, JwtTokenAdapter jwt) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)            // API sin sesión de navegador: no aplica CSRF
            .cors(cors -> { })                                // usa la configuración CORS de abajo
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // cada petición trae su token
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(rutas -> rutas
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Públicas: iniciar sesión y recuperar contraseña (HU-01 y HU-02)
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/recuperar", "/api/auth/restablecer").permitAll()
                .requestMatchers("/error").permitAll()
                // Solo el administrador gestiona cuentas (HU-03)
                .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                // Todo lo demás requiere haber iniciado sesión
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Permite que el frontend Angular (otro puerto) llame al API desde el navegador. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${restoflow.cors.origenes-permitidos}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
