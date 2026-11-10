package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.CambiarContrasenaUseCase;
import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Usuario;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mi cuenta · Cambiar la contraseña con la sesión iniciada (distinto de "Recuperar contraseña", que es para quien
 * la olvidó y usa un código enviado al correo).
 */
@Transactional
public class CambiarContrasenaService implements CambiarContrasenaUseCase {

    private final UsuarioRepositoryPort usuarios;
    private final CifradoContrasenaPort cifrado;

    public CambiarContrasenaService(UsuarioRepositoryPort usuarios, CifradoContrasenaPort cifrado) {
        this.usuarios = usuarios;
        this.cifrado = cifrado;
    }

    /**
     * Si la contraseña actual no coincide se responde 400 (no 401): un 401 haría que el frontend cerrara la sesión,
     * y el usuario solo se equivocó al escribirla.
     */
    @Override
    public void cambiar(Long usuarioId, String actual, String nueva) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId));
        if (actual == null || !cifrado.coincide(actual, usuario.getPasswordHash())) {
            Auditoria.cambioContrasenaRechazado(usuario.getEmail());
            throw new DomainException("La contraseña actual no es correcta");
        }
        Usuario.validarContrasenaSegura(nueva);
        if (nueva.equals(actual)) {
            throw new DomainException("La nueva contraseña debe ser distinta de la actual");
        }
        usuario.cambiarContrasena(cifrado.cifrar(nueva));
        usuarios.guardar(usuario);
        Auditoria.contrasenaCambiada(usuario.getEmail());
    }
}
