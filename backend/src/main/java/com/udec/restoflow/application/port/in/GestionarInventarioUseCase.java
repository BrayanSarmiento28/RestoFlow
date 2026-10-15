package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.domain.model.UnidadMedida;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * RF-05 · El personal de inventario consulta y actualiza los insumos.
 * El stock solo cambia con movimientos (entrada, salida o ajuste) y cada uno queda en el historial (RF-12).
 */
public interface GestionarInventarioUseCase {

    List<Ingrediente> listar(boolean soloActivos);

    Ingrediente obtener(Long id);

    /** Crea el insumo; si trae stock inicial se registra como una entrada. */
    Ingrediente crear(ComandoIngrediente comando, Long usuarioId);

    /** Cambia nombre, unidad y stock mínimo (no el stock actual). */
    Ingrediente actualizar(Long id, ComandoIngrediente comando);

    /** Activa o desactiva un insumo (nunca se borra, para no perder su historial). */
    Ingrediente cambiarEstado(Long id, boolean activo);

    /** RES-32 · Compra o ingreso de mercancía. */
    Ingrediente registrarEntrada(Long id, BigDecimal cantidad, String motivo, Long usuarioId);

    /** RES-32 · Merma, daño o vencimiento. No permite sacar más de lo que hay. */
    Ingrediente registrarSalida(Long id, BigDecimal cantidad, String motivo, Long usuarioId);

    /** Deja el stock en lo que se contó físicamente. */
    Ingrediente ajustar(Long id, BigDecimal stockContado, String motivo, Long usuarioId);

    /** RF-12 · Historial de movimientos. Todos los filtros son opcionales; del más reciente al más antiguo. */
    List<MovimientoInventario> movimientos(Long ingredienteId, TipoMovimiento tipo, LocalDate desde, LocalDate hasta);

    /** stockInicial solo se usa al crear. */
    record ComandoIngrediente(String nombre, UnidadMedida unidad, BigDecimal stockMinimo, BigDecimal stockInicial) { }
}
