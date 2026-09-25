package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase;
import com.udec.restoflow.application.port.in.GestionarCuentasUseCase.ComandoCrearCuenta;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.model.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * HU-03 · Como solo un administrador puede crear cuentas, alguien tiene que ser el primero.
 * Al arrancar, si la tabla de usuarios está vacía, se crea el administrador inicial.
 */
@Component
public class AdministradorInicialConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicialConfig.class);

    private final UsuarioRepositoryPort usuarios;
    private final GestionarCuentasUseCase cuentas;
    private final String nombre;
    private final String email;
    private final String contrasena;

    public AdministradorInicialConfig(UsuarioRepositoryPort usuarios, GestionarCuentasUseCase cuentas,
                                      @Value("${restoflow.admin-inicial.nombre}") String nombre,
                                      @Value("${restoflow.admin-inicial.email}") String email,
                                      @Value("${restoflow.admin-inicial.contrasena}") String contrasena) {
        this.usuarios = usuarios;
        this.cuentas = cuentas;
        this.nombre = nombre;
        this.email = email;
        this.contrasena = contrasena;
    }

    @Override
    public void run(String... args) {
        if (usuarios.contar() > 0) {
            return;
        }
        cuentas.crearCuenta(new ComandoCrearCuenta(nombre, email, contrasena, Rol.ADMIN));
        log.warn("Se creó el administrador inicial ({}). Cambie su contraseña después del primer ingreso.", email);
    }
}
