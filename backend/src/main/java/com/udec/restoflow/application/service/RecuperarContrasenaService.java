package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.RecuperarContrasenaUseCase;
import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import com.udec.restoflow.application.port.out.CodigoRecuperacionRepositoryPort;
import com.udec.restoflow.application.port.out.EnviarCorreoPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.exception.CodigoRecuperacionInvalidoException;
import com.udec.restoflow.domain.model.CodigoRecuperacion;
import com.udec.restoflow.domain.model.Usuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * HU-02 · Recuperar contraseña con un código de 6 dígitos enviado al correo.
 */
public class RecuperarContrasenaService implements RecuperarContrasenaUseCase {

    private final UsuarioRepositoryPort usuarios;
    private final CodigoRecuperacionRepositoryPort codigos;
    private final CifradoContrasenaPort cifrado;
    private final EnviarCorreoPort correo;
    private final Clock reloj;

    public RecuperarContrasenaService(UsuarioRepositoryPort usuarios, CodigoRecuperacionRepositoryPort codigos,
                                      CifradoContrasenaPort cifrado, EnviarCorreoPort correo, Clock reloj) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.cifrado = cifrado;
        this.correo = correo;
        this.reloj = reloj;
    }

    /**
     * Paso 1. Si el correo no existe o la cuenta está inactiva, no se hace nada y NO se avisa:
     * así nadie puede averiguar qué correos están registrados en el sistema.
     */
    @Override
    public void solicitarCodigo(String email) {
        Optional<Usuario> cuenta = usuarios.buscarPorEmail(Usuario.normalizarEmail(email)).filter(Usuario::isActivo);
        if (cuenta.isEmpty()) {
            return;
        }
        Usuario usuario = cuenta.get();

        codigos.invalidarCodigosDelUsuario(usuario.getId()); // solo sirve el código más reciente
        String codigoPlano = CodigoRecuperacion.generarCodigoPlano();
        codigos.guardar(CodigoRecuperacion.nuevo(usuario.getId(), cifrado.cifrar(codigoPlano), ahora()));

        correo.enviarCodigoRecuperacion(usuario.getEmail(), usuario.getNombre(), codigoPlano,
                CodigoRecuperacion.VIGENCIA_MINUTOS);
    }

    /** Paso 2. Valida el código y, si es correcto, cambia la contraseña. */
    @Override
    public void restablecerContrasena(ComandoRestablecer comando) {
        Usuario.validarContrasenaSegura(comando.nuevaContrasena());

        Usuario usuario = usuarios.buscarPorEmail(Usuario.normalizarEmail(comando.email()))
                .orElseThrow(CodigoRecuperacionInvalidoException::new);
        CodigoRecuperacion codigo = codigos.buscarUltimoSinUsar(usuario.getId())
                .filter(c -> c.estaVigente(ahora()))
                .orElseThrow(CodigoRecuperacionInvalidoException::new);

        if (comando.codigo() == null || !cifrado.coincide(comando.codigo().trim(), codigo.getCodigoHash())) {
            codigo.registrarIntentoFallido();
            codigos.guardar(codigo);
            throw new CodigoRecuperacionInvalidoException();
        }

        codigo.usar(ahora());
        codigos.guardar(codigo);

        usuario.cambiarContrasena(cifrado.cifrar(comando.nuevaContrasena()));
        usuarios.guardar(usuario);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
