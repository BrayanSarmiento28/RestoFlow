package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.GestionarInventarioUseCase;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.MovimientoRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.TipoMovimiento;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-05 · Insumos y movimientos manuales. Cada cambio de stock guarda su movimiento y revisa las alertas
 * en la misma transacción: o queda todo registrado o no queda nada.
 */
@Transactional
public class GestionarInventarioService implements GestionarInventarioUseCase {

    private final IngredienteRepositoryPort ingredientes;
    private final MovimientoRepositoryPort movimientos;
    private final ControlAlertas control;
    private final Clock reloj;

    public GestionarInventarioService(IngredienteRepositoryPort ingredientes, MovimientoRepositoryPort movimientos,
                                      ControlAlertas control, Clock reloj) {
        this.ingredientes = ingredientes;
        this.movimientos = movimientos;
        this.control = control;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingrediente> listar(boolean soloActivos) {
        return ingredientes.listar(soloActivos);
    }

    @Override
    @Transactional(readOnly = true)
    public Ingrediente obtener(Long id) {
        return ingredientes.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Ingrediente", id));
    }

    @Override
    public Ingrediente crear(ComandoIngrediente comando, Long usuarioId) {
        Ingrediente nuevo = Ingrediente.nuevo(comando.nombre(), comando.unidad(), comando.stockInicial(),
                comando.stockMinimo());
        if (ingredientes.existePorNombre(nuevo.getNombre())) {
            throw new OperacionNoPermitidaException("Ya existe el ingrediente " + nuevo.getNombre());
        }
        Ingrediente guardado = ingredientes.guardar(nuevo);
        if (guardado.getStockActual().signum() > 0) {
            registrar(guardado, TipoMovimiento.ENTRADA, guardado.getStockActual(), "Stock inicial", usuarioId);
            Auditoria.movimientoInventario("ENTRADA", guardado.getNombre(), guardado.getStockActual(),
                    guardado.getStockActual(), usuarioId);
        }
        control.revisar(guardado);
        return guardado;
    }

    @Override
    public Ingrediente actualizar(Long id, ComandoIngrediente comando) {
        Ingrediente ingrediente = obtener(id);
        String nombreAnterior = ingrediente.getNombre();
        ingrediente.actualizar(comando.nombre(), comando.unidad(), comando.stockMinimo());
        if (!ingrediente.getNombre().equalsIgnoreCase(nombreAnterior) && ingredientes.existePorNombre(ingrediente.getNombre())) {
            throw new OperacionNoPermitidaException("Ya existe el ingrediente " + ingrediente.getNombre());
        }
        Ingrediente guardado = ingredientes.guardar(ingrediente);
        control.revisar(guardado);              // cambiar el mínimo puede abrir o cerrar una alerta
        return guardado;
    }

    @Override
    public Ingrediente cambiarEstado(Long id, boolean activo) {
        Ingrediente ingrediente = obtener(id);
        if (activo) {
            ingrediente.activar();
        } else {
            ingrediente.desactivar();
        }
        return ingredientes.guardar(ingrediente);
    }

    @Override
    public Ingrediente registrarEntrada(Long id, BigDecimal cantidad, String motivo, Long usuarioId) {
        Ingrediente ingrediente = obtener(id);
        ingrediente.ingresar(cantidad);
        return guardarConMovimiento(ingrediente, TipoMovimiento.ENTRADA, cantidad, motivo, usuarioId);
    }

    @Override
    public Ingrediente registrarSalida(Long id, BigDecimal cantidad, String motivo, Long usuarioId) {
        if (motivo == null || motivo.isBlank()) {
            throw new DomainException("Indique el motivo de la salida (merma, daño, vencimiento...)");
        }
        Ingrediente ingrediente = obtener(id);
        ingrediente.descontar(cantidad);
        return guardarConMovimiento(ingrediente, TipoMovimiento.SALIDA, cantidad, motivo, usuarioId);
    }

    @Override
    public Ingrediente ajustar(Long id, BigDecimal stockContado, String motivo, Long usuarioId) {
        Ingrediente ingrediente = obtener(id);
        BigDecimal anterior = ingrediente.getStockActual();
        ingrediente.ajustarA(stockContado);
        String detalle = "Conteo físico: había " + ControlAlertas.numero(anterior) + " registrado"
                + (motivo == null || motivo.isBlank() ? "" : ". " + motivo.trim());
        return guardarConMovimiento(ingrediente, TipoMovimiento.AJUSTE, stockContado, detalle, usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimientoInventario> movimientos(Long ingredienteId, TipoMovimiento tipo, LocalDate desde,
                                                  LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new DomainException("La fecha final no puede ser anterior a la inicial");
        }
        return movimientos.buscar(ingredienteId, tipo,
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay());
    }

    private Ingrediente guardarConMovimiento(Ingrediente ingrediente, TipoMovimiento tipo, BigDecimal cantidad,
                                             String motivo, Long usuarioId) {
        Ingrediente guardado = ingredientes.guardar(ingrediente);
        registrar(guardado, tipo, cantidad, motivo, usuarioId);
        Auditoria.movimientoInventario(tipo.name(), guardado.getNombre(), cantidad, guardado.getStockActual(), usuarioId);
        control.revisar(guardado);
        return guardado;
    }

    private void registrar(Ingrediente ingrediente, TipoMovimiento tipo, BigDecimal cantidad, String motivo,
                           Long usuarioId) {
        movimientos.guardar(MovimientoInventario.registrar(ingrediente.getId(), tipo, cantidad, motivo, usuarioId,
                null, LocalDateTime.now(reloj)));
    }
}
