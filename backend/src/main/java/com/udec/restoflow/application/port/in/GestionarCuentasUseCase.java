package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;

import java.util.List;

/**
 * HU-03 · Crear cuentas del personal y desactivarlas o reactivarlas (solo el administrador).
 */
public interface GestionarCuentasUseCase {

    Usuario crearCuenta(ComandoCrearCuenta comando);

    List<Usuario> listarCuentas();

    Usuario obtenerCuenta(Long id);

    /**
     * Desactiva (activa = false) o reactiva (activa = true) una cuenta. Una cuenta desactivada no puede iniciar
     * sesión y su sesión abierta deja de funcionar; su historial (pedidos, ventas, movimientos) se conserva.
     *
     * @param administradorId quien hace el cambio: no puede desactivar su propia cuenta
     */
    Usuario cambiarEstado(Long id, boolean activa, Long administradorId);

    record ComandoCrearCuenta(String nombre, String email, String contrasena, Rol rol) { }
}
