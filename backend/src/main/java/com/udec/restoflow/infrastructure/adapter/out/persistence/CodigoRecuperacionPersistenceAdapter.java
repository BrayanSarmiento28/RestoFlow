package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.CodigoRecuperacionRepositoryPort;
import com.udec.restoflow.domain.model.CodigoRecuperacion;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.CodigoRecuperacionEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.CodigoRecuperacionJpaRepository;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.UsuarioJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adaptador de salida: implementa CodigoRecuperacionRepositoryPort con JPA y SQL Server. */
@Component
public class CodigoRecuperacionPersistenceAdapter implements CodigoRecuperacionRepositoryPort {

    private final CodigoRecuperacionJpaRepository repositorio;
    private final UsuarioJpaRepository usuarios;

    public CodigoRecuperacionPersistenceAdapter(CodigoRecuperacionJpaRepository repositorio,
                                                UsuarioJpaRepository usuarios) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
    }

    @Override
    public CodigoRecuperacion guardar(CodigoRecuperacion codigo) {
        CodigoRecuperacionEntity entidad = codigo.getId() == null
                ? new CodigoRecuperacionEntity()
                : repositorio.findById(codigo.getId()).orElseGet(CodigoRecuperacionEntity::new);
        entidad.setUsuario(usuarios.getReferenceById(codigo.getUsuarioId()));
        entidad.setCodigoHash(codigo.getCodigoHash());
        entidad.setFechaExpiracion(codigo.getFechaExpiracion());
        entidad.setUsado(codigo.isUsado());
        entidad.setIntentosFallidos(codigo.getIntentosFallidos());
        return PersistenceMapper.aDominio(repositorio.save(entidad));
    }

    @Override
    public Optional<CodigoRecuperacion> buscarUltimoSinUsar(Long usuarioId) {
        return repositorio.findFirstByUsuario_IdAndUsadoFalseOrderByIdDesc(usuarioId).map(PersistenceMapper::aDominio);
    }

    @Override
    public void invalidarCodigosDelUsuario(Long usuarioId) {
        repositorio.marcarComoUsadosLosDelUsuario(usuarioId);
    }
}
