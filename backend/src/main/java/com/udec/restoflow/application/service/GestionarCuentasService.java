package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase;
import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.exception.CorreoYaRegistradoException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Usuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-03 · El administrador crea las cuentas del personal, les asigna un rol y las desactiva o reactiva.
 * <p>
 * Las cuentas no se borran: los pedidos, las ventas por mesero y los movimientos de inventario guardan quién
 * los hizo. Desactivar corta el acceso sin perder ese historial, y se puede deshacer.
 * (El control de que solo un ADMIN pueda hacerlo se aplica en la capa de seguridad, paso B7.)
 */
@Transactional
public class GestionarCuentasService implements GestionarCuentasUseCase {

    private final UsuarioRepositoryPort usuarios;
    private final CifradoContrasenaPort cifrado;
    private final Clock reloj;

    public GestionarCuentasService(UsuarioRepositoryPort usuarios, CifradoContrasenaPort cifrado, Clock reloj) {
        this.usuarios = usuarios;
        this.cifrado = cifrado;
        this.reloj = reloj;
    }

    @Override
    public Usuario crearCuenta(ComandoCrearCuenta comando) {
        Usuario.validarContrasenaSegura(comando.contrasena());
        String email = Usuario.normalizarEmail(comando.email());
        if (usuarios.existePorEmail(email)) {
            throw new CorreoYaRegistradoException(email);
        }
        Usuario nuevo = Usuario.nuevo(comando.nombre(), email, cifrado.cifrar(comando.contrasena()),
                comando.rol(), LocalDateTime.now(reloj));
        Usuario guardado = usuarios.guardar(nuevo);
        Auditoria.cuentaCreada(guardado.getEmail(), guardado.getRol().name());
        return guardado;
    }

    @Override
    public List<Usuario> listarCuentas() {
        return usuarios.listarTodos();
    }

    @Override
    public Usuario obtenerCuenta(Long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    @Override
    public Usuario cambiarEstado(Long id, boolean activa, Long administradorId) {
        Usuario cuenta = obtenerCuenta(id);
        // Quien hace el cambio es un administrador activo; si no puede desactivarse a sí mismo,
        // siempre queda al menos un administrador con acceso al sistema.
        if (!activa && cuenta.getId().equals(administradorId)) {
            throw new OperacionNoPermitidaException("No puedes desactivar tu propia cuenta");
        }
        if (cuenta.isActivo() == activa) {
            return cuenta;
        }
        if (activa) {
            cuenta.activar();
        } else {
            cuenta.desactivar();
        }
        Usuario guardada = usuarios.guardar(cuenta);
        Auditoria.estadoCuentaCambiado(guardada.getEmail(), activa, administradorId);
        return guardada;
    }
}
