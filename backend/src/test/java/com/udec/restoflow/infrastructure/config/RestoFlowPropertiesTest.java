package com.udec.restoflow.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Factor III: la configuración llega del entorno y se valida al arrancar.
 * Se levanta solo esta clase de configuración (ApplicationContextRunner), sin base de datos ni servidor web.
 */
class RestoFlowPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(RestoFlowProperties.class)
    static class Configuracion {
    }

    private static final String[] MINIMO = {
            "restoflow.jwt.secret=clave-de-pruebas-con-mas-de-32-caracteres-ok",
            "restoflow.cors.origenes-permitidos=http://localhost:4200,https://restoflow.udec.edu.co",
            "restoflow.admin-inicial.email=admin@restoflow.com",
            "restoflow.admin-inicial.contrasena=Admin2026*"};

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Configuracion.class);

    @Test
    @DisplayName("Con lo mínimo arranca y completa los valores por defecto")
    void valoresPorDefecto() {
        contexto.withPropertyValues(MINIMO).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            RestoFlowProperties p = ctx.getBean(RestoFlowProperties.class);
            assertThat(p.entorno()).isEqualTo("desarrollo");
            assertThat(p.datosDemo()).isFalse();
            assertThat(p.jwt().expiracionMinutos()).isEqualTo(480);
            assertThat(p.cors().origenesPermitidos()).containsExactly("http://localhost:4200", "https://restoflow.udec.edu.co");
            assertThat(p.correo().modo()).isEqualTo("consola");
            assertThat(p.logs().directorio()).isEqualTo("logs");
            assertThat(p.apisExternas().tiempoEsperaSegundos()).isEqualTo(4);
            assertThat(p.apisExternas().openFoodFacts().url()).isEqualTo("https://world.openfoodfacts.org");
        });
    }

    @Test
    @DisplayName("Una clave JWT de menos de 32 caracteres impide arrancar")
    void claveCorta() {
        contexto.withPropertyValues(MINIMO).withPropertyValues("restoflow.jwt.secret=corta").run(ctx ->
                assertThat(ctx).hasFailed().getFailure().hasStackTraceContaining("JWT_SECRET"));
    }

    @Test
    @DisplayName("Sin JWT_SECRET (producción sin la variable) no arranca")
    void sinClave() {
        contexto.withPropertyValues(MINIMO[1], MINIMO[2], MINIMO[3]).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("Un modo de correo desconocido o un correo mal escrito impiden arrancar")
    void valoresImposibles() {
        contexto.withPropertyValues(MINIMO).withPropertyValues("restoflow.correo.modo=whatsapp").run(ctx ->
                assertThat(ctx).hasFailed().getFailure().hasStackTraceContaining("consola o gmail"));
        contexto.withPropertyValues(MINIMO).withPropertyValues("restoflow.admin-inicial.email=no-es-un-correo").run(ctx ->
                assertThat(ctx).hasFailed());
        contexto.withPropertyValues(MINIMO).withPropertyValues("restoflow.apis-externas.tiempo-espera-segundos=0").run(ctx ->
                assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("Las variables de entorno con nombre relajado también se leen (RESTOFLOW_JWT_EXPIRACIONMINUTOS)")
    void nombreRelajado() {
        contexto.withPropertyValues(MINIMO).withPropertyValues("restoflow.jwt.expiracionMinutos=60").run(ctx ->
                assertThat(ctx.getBean(RestoFlowProperties.class).jwt().expiracionMinutos()).isEqualTo(60));
    }

    @Test
    @DisplayName("Los secretos nunca aparecen al imprimir la configuración (por ejemplo en un log)")
    void secretosOcultos() {
        contexto.withPropertyValues(MINIMO).run(ctx -> {
            String texto = ctx.getBean(RestoFlowProperties.class).toString();
            assertThat(texto).doesNotContain("clave-de-pruebas").doesNotContain("Admin2026*").contains("****");
        });
    }
}
