package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.ProductoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoJpaRepository extends JpaRepository<ProductoEntity, Long> {

    /** Los filtros en null no se aplican. "join fetch" trae la categoría en la misma consulta. */
    @Query("""
            select p from ProductoEntity p join fetch p.categoria c
            where (:soloActivos = false or p.activo = true)
              and (:categoriaId is null or c.id = :categoriaId)
            order by c.nombre, p.nombre""")
    List<ProductoEntity> buscar(@Param("soloActivos") boolean soloActivos, @Param("categoriaId") Long categoriaId);
}
