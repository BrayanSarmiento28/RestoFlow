package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase;
import com.udec.restoflow.application.port.in.IniciarSesionUseCase;
import com.udec.restoflow.application.port.in.RecuperarContrasenaUseCase;
import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import com.udec.restoflow.application.port.out.CodigoRecuperacionRepositoryPort;
import com.udec.restoflow.application.port.out.EnviarCorreoPort;
import com.udec.restoflow.application.port.out.GeneradorTokenPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.application.service.GestionarCuentasService;
import com.udec.restoflow.application.service.IniciarSesionService;
import com.udec.restoflow.application.service.RecuperarContrasenaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Aquí se "conecta" el hexágono: Spring crea cada caso de uso y le entrega los adaptadores
 * que implementan sus puertos de salida (inyección de dependencias).
 * Por eso las clases de la capa de aplicación no necesitan anotaciones como @Service.
 */
@Configuration
public class BeanConfig {

    /** Reloj del sistema. En las pruebas se reemplaza por uno fijo para simular el paso del tiempo. */
    @Bean
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public IniciarSesionUseCase iniciarSesionUseCase(UsuarioRepositoryPort usuarios, CifradoContrasenaPort cifrado,
                                                     GeneradorTokenPort generadorToken) {
        return new IniciarSesionService(usuarios, cifrado, generadorToken);
    }

    @Bean
    public RecuperarContrasenaUseCase recuperarContrasenaUseCase(UsuarioRepositoryPort usuarios,
                                                                 CodigoRecuperacionRepositoryPort codigos,
                                                                 CifradoContrasenaPort cifrado,
                                                                 EnviarCorreoPort correo, Clock reloj) {
        return new RecuperarContrasenaService(usuarios, codigos, cifrado, correo, reloj);
    }

    @Bean
    public GestionarCuentasUseCase gestionarCuentasUseCase(UsuarioRepositoryPort usuarios,
                                                           CifradoContrasenaPort cifrado, Clock reloj) {
        return new GestionarCuentasService(usuarios, cifrado, reloj);
    }
}
