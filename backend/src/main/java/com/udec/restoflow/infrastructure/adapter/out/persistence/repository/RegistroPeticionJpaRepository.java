package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.RegistroPeticionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RegistroPeticionJpaRepository extends JpaRepository<RegistroPeticionEntity, Long> {

    /** Las mismas condiciones para la lista y el resumen (texto en minúsculas para buscar sin importar mayúsculas). */
    String CONDICIONES = """
            where (cast(:desde as LocalDateTime) is null or r.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or r.fecha < :hasta)
              and (:usuario is null or lower(r.usuario) like :usuario)
              and (:metodo is null or r.metodo = :metodo)
              and (:ruta is null or lower(r.ruta) like :ruta)
              and r.estado between :estadoDesde and :estadoHasta
              and r.duracionMs >= :duracionMinima""";

    @Query(value = "select r from RegistroPeticionEntity r " + CONDICIONES + " order by r.fecha desc, r.id desc",
            countQuery = "select count(r) from RegistroPeticionEntity r " + CONDICIONES)
    // Spring Data recibe cada filtro de la consulta como un parámetro propio: agruparlos en un objeto obligaría a
    // usar expresiones SpEL dentro del JPQL, más difíciles de leer y de probar. Por eso se acepta pasar de 7.
    Page<RegistroPeticionEntity> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,   // NOSONAR java:S107
                                        @Param("usuario") String usuario, @Param("metodo") String metodo,
                                        @Param("ruta") String ruta, @Param("estadoDesde") int estadoDesde,
                                        @Param("estadoHasta") int estadoHasta,
                                        @Param("duracionMinima") long duracionMinima, Pageable pagina);

    /** total, 4xx, 5xx, lentas y duración promedio, en una sola consulta. */
    @Query("select count(r), "
            + "coalesce(sum(case when r.estado >= 400 and r.estado < 500 then 1 else 0 end), 0), "
            + "coalesce(sum(case when r.estado >= 500 then 1 else 0 end), 0), "
            + "coalesce(sum(case when r.duracionMs > 1000 then 1 else 0 end), 0), "
            + "coalesce(avg(r.duracionMs), 0) "
            + "from RegistroPeticionEntity r " + CONDICIONES)
    // Misma razón que buscar(...): un parámetro por filtro de la consulta
    Object[] resumir(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,   // NOSONAR java:S107
                     @Param("usuario") String usuario, @Param("metodo") String metodo, @Param("ruta") String ruta,
                     @Param("estadoDesde") int estadoDesde, @Param("estadoHasta") int estadoHasta,
                     @Param("duracionMinima") long duracionMinima);

    @Modifying
    @Query("delete from RegistroPeticionEntity r where r.fecha < :limite")
    int borrarAnterioresA(@Param("limite") LocalDateTime limite);
}
