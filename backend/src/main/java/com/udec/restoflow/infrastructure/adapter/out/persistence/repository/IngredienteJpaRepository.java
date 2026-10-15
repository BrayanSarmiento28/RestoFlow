package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.IngredienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngredienteJpaRepository extends JpaRepository<IngredienteEntity, Long> {

    List<IngredienteEntity> findAllByOrderByNombreAsc();

    List<IngredienteEntity> findByActivoTrueOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
