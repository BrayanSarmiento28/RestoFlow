package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;

import java.util.List;

/**
 * HU-03 · Crear cuentas del personal (solo el administrador).
 */
public interface GestionarCuentasUseCase {

    Usuario crearCuenta(ComandoCrearCuenta comando);

    List<Usuario> listarCuentas();

    Usuario obtenerCuenta(Long id);

    record ComandoCrearCuenta(String nombre, String email, String contrasena, Rol rol) { }
}
