package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import com.udec.restoflow.application.port.out.CodigoRecuperacionRepositoryPort;
import com.udec.restoflow.application.port.out.EnviarCorreoPort;
import com.udec.restoflow.application.port.out.GeneradorTokenPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.model.CodigoRecuperacion;
import com.udec.restoflow.domain.model.Usuario;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Dobles de prueba": implementaciones falsas y sencillas de los puertos de salida.
 * Gracias a la arquitectura hexagonal, los casos de uso se prueban sin base de datos, sin BCrypt
 * y sin Gmail: basta con entregarles estas versiones en memoria.
 */
final class Dobles {

    private Dobles() {
    }

    /** Guarda usuarios en un mapa en memoria. */
    static class UsuariosEnMemoria implements UsuarioRepositoryPort {
        final Map<Long, Usuario> datos = new LinkedHashMap<>();
        private long secuencia = 1;

        @Override
        public Usuario guardar(Usuario u) {
            Long id = u.getId() != null ? u.getId() : secuencia++;
            Usuario copia = new Usuario(id, u.getNombre(), u.getEmail(), u.getPasswordHash(), u.getRol(),
                    u.isActivo(), u.getFechaCreacion());
            datos.put(id, copia);
            return copia;
        }

        @Override
        public Optional<Usuario> buscarPorId(Long id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Usuario> buscarPorEmail(String email) {
            return datos.values().stream().filter(u -> u.getEmail().equals(email)).findFirst();
        }

        @Override
        public boolean existePorEmail(String email) {
            return buscarPorEmail(email).isPresent();
        }

        @Override
        public List<Usuario> listarTodos() {
            return new ArrayList<>(datos.values());
        }

        @Override
        public long contar() {
            return datos.size();
        }
    }

    /** Guarda códigos de recuperación en memoria. */
    static class CodigosEnMemoria implements CodigoRecuperacionRepositoryPort {
        final Map<Long, CodigoRecuperacion> datos = new LinkedHashMap<>();
        private long secuencia = 1;

        @Override
        public CodigoRecuperacion guardar(CodigoRecuperacion c) {
            Long id = c.getId() != null ? c.getId() : secuencia++;
            CodigoRecuperacion copia = new CodigoRecuperacion(id, c.getUsuarioId(), c.getCodigoHash(),
                    c.getFechaExpiracion(), c.isUsado(), c.getIntentosFallidos());
            datos.put(id, copia);
            return copia;
        }

        @Override
        public Optional<CodigoRecuperacion> buscarUltimoSinUsar(Long usuarioId) {
            return datos.values().stream()
                    .filter(c -> c.getUsuarioId().equals(usuarioId) && !c.isUsado())
                    .reduce((primero, segundo) -> segundo);
        }

        @Override
        public void invalidarCodigosDelUsuario(Long usuarioId) {
            datos.replaceAll((id, c) -> c.getUsuarioId().equals(usuarioId) && !c.isUsado()
                    ? new CodigoRecuperacion(id, c.getUsuarioId(), c.getCodigoHash(), c.getFechaExpiracion(),
                            true, c.getIntentosFallidos())
                    : c);
        }
    }

    /** "Cifra" agregando un prefijo: suficiente para probar la lógica sin el costo de BCrypt. */
    static class CifradoFalso implements CifradoContrasenaPort {
        @Override
        public String cifrar(String textoPlano) {
            return "cifrado:" + textoPlano;
        }

        @Override
        public boolean coincide(String textoPlano, String hash) {
            return hash.equals("cifrado:" + textoPlano);
        }
    }

    static class TokenFalso implements GeneradorTokenPort {
        @Override
        public String generarToken(Usuario usuario) {
            return "token-de-" + usuario.getEmail();
        }

        @Override
        public long getVigenciaSegundos() {
            return 3600;
        }
    }

    /** En lugar de enviar el correo, recuerda el último código para que la prueba lo use. */
    static class CorreoFalso implements EnviarCorreoPort {
        String ultimoDestinatario;
        String ultimoCodigo;
        int enviados;

        @Override
        public void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo, int minutos) {
            this.ultimoDestinatario = destinatario;
            this.ultimoCodigo = codigo;
            this.enviados++;
        }
    }

    /** Reloj que se puede adelantar manualmente para simular el paso del tiempo. */
    static class RelojManual extends Clock {
        private Instant instante = Instant.parse("2026-09-25T15:00:00Z");

        void avanzarMinutos(long minutos) {
            instante = instante.plusSeconds(minutos * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Bogota");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
