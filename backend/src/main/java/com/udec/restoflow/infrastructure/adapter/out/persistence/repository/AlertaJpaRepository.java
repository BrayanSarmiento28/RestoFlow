package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.AlertaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaJpaRepository extends JpaRepository<AlertaEntity, Long> {

    @EntityGraph(attributePaths = "ingrediente")
    List<AlertaEntity> findAllByOrderByFechaCreacionDescIdDesc();

    @EntityGraph(attributePaths = "ingrediente")
    List<AlertaEntity> findByAtendidaFalseOrderByFechaCreacionDescIdDesc();

    @EntityGraph(attributePaths = "ingrediente")
    List<AlertaEntity> findByIngredienteIdAndAtendidaFalse(Long ingredienteId);

    long countByAtendidaFalse();
}
