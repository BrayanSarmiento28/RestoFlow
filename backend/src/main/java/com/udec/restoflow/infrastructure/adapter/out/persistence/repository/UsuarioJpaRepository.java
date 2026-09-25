package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data genera la implementación (el SQL) a partir del nombre de cada método. */
public interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, Long> {

    Optional<UsuarioEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UsuarioEntity> findAllByOrderByNombreAsc();
}
