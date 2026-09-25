package com.udec.restoflow.infrastructure.adapter.out.persistence.mapper;

import com.udec.restoflow.domain.model.CodigoRecuperacion;
import com.udec.restoflow.domain.model.Usuario;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.CodigoRecuperacionEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.UsuarioEntity;

/** Convierte entidades JPA en modelos de dominio. Así el dominio nunca ve anotaciones de base de datos. */
public final class PersistenceMapper {

    private PersistenceMapper() {
    }

    public static Usuario aDominio(UsuarioEntity e) {
        return new Usuario(e.getId(), e.getNombre(), e.getEmail(), e.getPasswordHash(), e.getRol(), e.isActivo(),
                e.getFechaCreacion());
    }

    public static CodigoRecuperacion aDominio(CodigoRecuperacionEntity e) {
        return new CodigoRecuperacion(e.getId(), e.getUsuario().getId(), e.getCodigoHash(), e.getFechaExpiracion(),
                e.isUsado(), e.getIntentosFallidos());
    }
}
