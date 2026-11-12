package com.udec.restoflow.application.port.in;

/**
 * Borra los registros de actividad más viejos que el tiempo de conservación (por defecto 30 días), para que la
 * tabla no crezca sin límite. Lo ejecuta una tarea programada cada madrugada.
 */
public interface LimpiarRegistroUseCase {

    /** @return cuántos registros se borraron */
    int limpiar();
}
