package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.TipoMovimiento;

import java.time.LocalDateTime;
import java.util.List;

/** Puerto de salida: historial de movimientos de inventario (solo se agregan, nunca se editan). */
public interface MovimientoRepositoryPort {

    MovimientoInventario guardar(MovimientoInventario movimiento);

    /** Filtros opcionales (null = sin filtro). Del más reciente al más antiguo. */
    List<MovimientoInventario> buscar(Long ingredienteId, TipoMovimiento tipo, LocalDateTime desde, LocalDateTime hasta);
}
