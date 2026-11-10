package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.MovimientoInventarioEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoInventarioJpaRepository extends JpaRepository<MovimientoInventarioEntity, Long> {

    @EntityGraph(attributePaths = {"ingrediente", "usuario", "pedido"})
    @Query("""
            select m from MovimientoInventarioEntity m
            where (:ingrediente is null or m.ingrediente.id = :ingrediente)
              and (:tipo is null or m.tipo = :tipo)
              and (cast(:desde as LocalDateTime) is null or m.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or m.fecha < :hasta)
            order by m.fecha desc, m.id desc""")
    List<MovimientoInventarioEntity> buscar(@Param("ingrediente") Long ingrediente, @Param("tipo") TipoMovimiento tipo,
                                            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
