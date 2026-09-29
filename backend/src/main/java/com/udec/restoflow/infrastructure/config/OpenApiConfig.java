package com.udec.restoflow.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación del API con Swagger (OpenAPI 3).
 * Queda disponible en http://localhost:8080/swagger-ui.html (desarrollo y pre-producción).
 * El botón "Authorize" permite pegar el token JWT para probar las rutas protegidas.
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad que usan las rutas protegidas. */
    public static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI restoFlowOpenApi(@Value("${restoflow.entorno:desarrollo}") String entorno) {
        return new OpenAPI()
                .info(new Info()
                        .title("RestoFlow API")
                        .version("0.1.0")
                        .description("API REST de RestoFlow · Sprint 1 (RF-01 Autenticación y control de acceso). "
                                + "Entorno: " + entorno + ". "
                                + "Para probar las rutas protegidas: ejecute POST /api/auth/login, copie el token "
                                + "y péguelo en el botón Authorize.")
                        .contact(new Contact().name("Equipo RestoFlow · Universidad de Cundinamarca")))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token que devuelve POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
