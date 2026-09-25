package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase;
import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.exception.CorreoYaRegistradoException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Usuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-03 · El administrador crea las cuentas del personal y les asigna un rol.
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
        return usuarios.guardar(nuevo);
    }

    @Override
    public List<Usuario> listarCuentas() {
        return usuarios.listarTodos();
    }

    @Override
    public Usuario obtenerCuenta(Long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }
}
