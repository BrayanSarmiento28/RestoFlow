package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.CodigoRecuperacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CodigoRecuperacionJpaRepository extends JpaRepository<CodigoRecuperacionEntity, Long> {

    Optional<CodigoRecuperacionEntity> findFirstByUsuario_IdAndUsadoFalseOrderByIdDesc(Long usuarioId);

    @Modifying
    @Query("update CodigoRecuperacionEntity c set c.usado = true where c.usuario.id = :usuarioId and c.usado = false")
    int marcarComoUsadosLosDelUsuario(@Param("usuarioId") Long usuarioId);
}
