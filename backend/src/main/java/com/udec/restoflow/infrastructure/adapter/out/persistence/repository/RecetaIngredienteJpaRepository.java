package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.RecetaIngredienteEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecetaIngredienteJpaRepository extends JpaRepository<RecetaIngredienteEntity, Long> {

    @EntityGraph(attributePaths = {"producto", "ingrediente"})
    @Query("select r from RecetaIngredienteEntity r where r.producto.id in :productos order by r.ingrediente.nombre")
    List<RecetaIngredienteEntity> deProductos(@Param("productos") Collection<Long> productos);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecetaIngredienteEntity r where r.producto.id = :producto")
    void borrarDeProducto(@Param("producto") Long producto);
}
