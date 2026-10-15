package com.udec.restoflow.infrastructure.adapter.out.persistence.repository;

import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.PedidoEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @EntityGraph indica que, junto con el pedido, se traigan el mesero y las líneas con su producto
 * en la misma consulta (evita cientos de consultas pequeñas y ayuda a cumplir los 3 segundos).
 */
public interface PedidoJpaRepository extends JpaRepository<PedidoEntity, Long> {

    @Override
    @EntityGraph(attributePaths = {"mesero", "detalles", "detalles.producto"})
    Optional<PedidoEntity> findById(Long id);

    @EntityGraph(attributePaths = {"mesero", "detalles", "detalles.producto"})
    @Query("""
            select p from PedidoEntity p
            where (:mesa is null or p.numeroMesa = :mesa)
              and (:estadoCocina is null or p.estadoCocina = :estadoCocina)
              and (:estadoPedido is null or p.estadoPedido = :estadoPedido)
            order by p.fechaCreacion desc""")
    List<PedidoEntity> buscar(@Param("mesa") Integer mesa, @Param("estadoCocina") EstadoCocina estadoCocina,
                              @Param("estadoPedido") EstadoPedido estadoPedido);

    @EntityGraph(attributePaths = {"mesero", "detalles", "detalles.producto"})
    @Query("""
            select p from PedidoEntity p
            where p.estadoPedido = com.udec.restoflow.domain.model.EstadoPedido.ABIERTO
              and p.estadoCocina <> com.udec.restoflow.domain.model.EstadoCocina.ENTREGADO
            order by p.fechaCreacion asc""")
    List<PedidoEntity> pendientesEnCocina();

    @EntityGraph(attributePaths = {"mesero", "detalles", "detalles.producto"})
    @Query("""
            select p from PedidoEntity p
            where p.fechaCreacion >= :desde and p.fechaCreacion < :hasta
              and (:estadoPedido is null or p.estadoPedido = :estadoPedido)
            order by p.fechaCreacion desc""")
    List<PedidoEntity> creadosEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                    @Param("estadoPedido") EstadoPedido estadoPedido);

    @EntityGraph(attributePaths = {"mesero", "detalles", "detalles.producto"})
    @Query("""
            select p from PedidoEntity p
            where p.estadoPedido = com.udec.restoflow.domain.model.EstadoPedido.PAGADO
              and p.fechaCierre >= :desde and p.fechaCierre < :hasta
            order by p.fechaCierre desc""")
    List<PedidoEntity> pagadosEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
