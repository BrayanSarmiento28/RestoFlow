package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.IngredienteEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.IngredienteJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adaptador de salida: insumos del inventario en la tabla "ingredientes". */
@Component
public class IngredientePersistenceAdapter implements IngredienteRepositoryPort {

    private final IngredienteJpaRepository repositorio;

    @PersistenceContext
    private EntityManager entityManager;

    public IngredientePersistenceAdapter(IngredienteJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Ingrediente guardar(Ingrediente ingrediente) {
        IngredienteEntity entidad = ingrediente.getId() == null
                ? new IngredienteEntity()
                : repositorio.findById(ingrediente.getId()).orElseGet(IngredienteEntity::new);
        entidad.setNombre(ingrediente.getNombre());
        entidad.setUnidad(ingrediente.getUnidad());
        entidad.setStockActual(ingrediente.getStockActual());
        entidad.setStockMinimo(ingrediente.getStockMinimo());
        entidad.setActivo(ingrediente.isActivo());
        return PersistenceMapper.aDominio(repositorio.save(entidad));
    }

    @Override
    public Optional<Ingrediente> buscarPorId(Long id) {
        return repositorio.findById(id).map(PersistenceMapper::aDominio);
    }

    @Override
    public List<Ingrediente> buscarPorIds(Collection<Long> ids) {
        return repositorio.findAllById(ids).stream().map(PersistenceMapper::aDominio).toList();
    }

    /**
     * Reserva cada insumo con un bloqueo pesimista de escritura (SQL Server: UPDLOCK; H2: FOR UPDATE), siempre en orden
     * de id para que dos transacciones no se esperen en círculo, y DESPUÉS de obtener el bloqueo lo vuelve a leer
     * ({@code refresh}).
     * <p>
     * Por qué el {@code refresh}: al leer la receta, Hibernate ya cargó el insumo en la memoria de la transacción (sin
     * bloqueo). Cuando luego llega la consulta con bloqueo, la base de datos devuelve el stock actualizado, pero Hibernate
     * conserva la copia que ya tenía. La prueba ConcurrenciaInventarioIntegracionTest lo demostró: con 10 cobros
     * simultáneos y solo el bloqueo, el stock bajaba 1 en vez de 10.
     */
    @Override
    public List<Ingrediente> bloquearPorIds(Collection<Long> ids) {
        List<Ingrediente> reservados = new ArrayList<>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            IngredienteEntity entidad = entityManager.find(IngredienteEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (entidad != null) {
                entityManager.refresh(entidad);
                reservados.add(PersistenceMapper.aDominio(entidad));
            }
        }
        return reservados;
    }

    @Override
    public List<Ingrediente> listar(boolean soloActivos) {
        List<IngredienteEntity> lista = soloActivos
                ? repositorio.findByActivoTrueOrderByNombreAsc()
                : repositorio.findAllByOrderByNombreAsc();
        return lista.stream().map(PersistenceMapper::aDominio).toList();
    }

    @Override
    public boolean existePorNombre(String nombre) {
        return repositorio.existsByNombreIgnoreCase(nombre);
    }

    @Override
    public long contar() {
        return repositorio.count();
    }
}
