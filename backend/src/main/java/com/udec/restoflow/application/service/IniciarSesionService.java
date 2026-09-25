package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.IniciarSesionUseCase;
import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import com.udec.restoflow.application.port.out.GeneradorTokenPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.exception.CredencialesInvalidasException;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.Usuario;

/**
 * HU-01 · Iniciar sesión.
 * <ol>
 *   <li>Busca la cuenta por correo.</li>
 *   <li>Compara la contraseña escrita con la cifrada.</li>
 *   <li>Verifica que la cuenta esté activa (regla del dominio).</li>
 *   <li>Genera el token de sesión.</li>
 * </ol>
 */
public class IniciarSesionService implements IniciarSesionUseCase {

    private final UsuarioRepositoryPort usuarios;
    private final CifradoContrasenaPort cifrado;
    private final GeneradorTokenPort generadorToken;

    public IniciarSesionService(UsuarioRepositoryPort usuarios, CifradoContrasenaPort cifrado,
                                GeneradorTokenPort generadorToken) {
        this.usuarios = usuarios;
        this.cifrado = cifrado;
        this.generadorToken = generadorToken;
    }

    @Override
    public ResultadoInicioSesion iniciarSesion(ComandoInicioSesion comando) {
        Usuario usuario = usuarios.buscarPorEmail(emailNormalizado(comando.email()))
                .filter(u -> comando.contrasena() != null && cifrado.coincide(comando.contrasena(), u.getPasswordHash()))
                .orElseThrow(CredencialesInvalidasException::new);

        usuario.validarPuedeIngresar();

        return new ResultadoInicioSesion(generadorToken.generarToken(usuario), generadorToken.getVigenciaSegundos(), usuario);
    }

    /** Un correo mal escrito se responde igual que uno inexistente: no damos pistas. */
    private static String emailNormalizado(String email) {
        try {
            return Usuario.normalizarEmail(email);
        } catch (DomainException e) {
            throw new CredencialesInvalidasException();
        }
    }
}
