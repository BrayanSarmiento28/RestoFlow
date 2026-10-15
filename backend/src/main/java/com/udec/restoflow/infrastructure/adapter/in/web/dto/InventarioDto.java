package com.udec.restoflow.infrastructure.adapter.in.web.dto;

import com.udec.restoflow.application.port.in.GestionarInventarioUseCase.ComandoIngrediente;
import com.udec.restoflow.application.port.in.GestionarRecetasUseCase.LineaReceta;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.domain.model.UnidadMedida;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** JSON de entrada y salida de inventario, recetas y alertas (RF-05 a RF-07, RF-12). */
public final class InventarioDto {

    private InventarioDto() {
    }

    // ---------- Entrada ----------

    public record IngredienteRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "Máximo 100 caracteres") String nombre,
            @NotNull(message = "La unidad de medida es obligatoria") UnidadMedida unidad,
            @NotNull(message = "El stock mínimo es obligatorio")
            @DecimalMin(value = "0", message = "El stock mínimo no puede ser negativo")
            @Digits(integer = 9, fraction = 3, message = "Máximo 3 decimales") BigDecimal stockMinimo,
            @DecimalMin(value = "0", message = "El stock inicial no puede ser negativo")
            @Digits(integer = 9, fraction = 3, message = "Máximo 3 decimales") BigDecimal stockInicial) {

        public ComandoIngrediente aComando() {
            return new ComandoIngrediente(nombre, unidad, stockMinimo, stockInicial);
        }
    }

    public record EstadoRequest(@NotNull(message = "Indique si queda activo") Boolean activo) { }

    /** Entrada (compra) o salida (merma). */
    public record MovimientoRequest(
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
            @Digits(integer = 9, fraction = 3, message = "Máximo 3 decimales") BigDecimal cantidad,
            @Size(max = 150, message = "El motivo admite máximo 150 caracteres") String motivo) { }

    public record AjusteRequest(
            @NotNull(message = "Indique cuánto contó")
            @DecimalMin(value = "0", message = "El conteo no puede ser negativo")
            @Digits(integer = 9, fraction = 3, message = "Máximo 3 decimales") BigDecimal stockContado,
            @Size(max = 120, message = "El motivo admite máximo 120 caracteres") String motivo) { }

    public record LineaRecetaRequest(
            @NotNull(message = "El ingrediente es obligatorio") Long ingredienteId,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
            @Digits(integer = 9, fraction = 3, message = "Máximo 3 decimales") BigDecimal cantidad) {

        public LineaReceta aLinea() {
            return new LineaReceta(ingredienteId, cantidad);
        }
    }

    public record RecetaRequest(@NotNull(message = "Envíe la lista de ingredientes (puede estar vacía)")
                                List<@Valid LineaRecetaRequest> lineas) { }

    // ---------- Salida ----------

    public record IngredienteResponse(Long id, String nombre, UnidadMedida unidad, BigDecimal stockActual,
                                      BigDecimal stockMinimo, boolean activo, boolean enMinimo, boolean agotado) {
        public static IngredienteResponse desde(Ingrediente i) {
            return new IngredienteResponse(i.getId(), i.getNombre(), i.getUnidad(), i.getStockActual(),
                    i.getStockMinimo(), i.isActivo(), i.estaEnMinimo(), i.estaAgotado());
        }
    }

    public record MovimientoResponse(Long id, Long ingredienteId, String ingredienteNombre, UnidadMedida unidad,
                                     TipoMovimiento tipo, BigDecimal cantidad, String motivo, Long usuarioId,
                                     String usuarioNombre, Long pedidoId, LocalDateTime fecha) {
        public static MovimientoResponse desde(MovimientoInventario m) {
            return new MovimientoResponse(m.getId(), m.getIngredienteId(), m.getIngredienteNombre(), m.getUnidad(),
                    m.getTipo(), m.getCantidad(), m.getMotivo(), m.getUsuarioId(),
                    m.getUsuarioId() == null ? "Sistema" : m.getUsuarioNombre(), m.getPedidoId(), m.getFecha());
        }
    }

    public record LineaRecetaResponse(Long ingredienteId, String ingredienteNombre, UnidadMedida unidad,
                                      BigDecimal cantidad) {
        public static LineaRecetaResponse desde(RecetaIngrediente r) {
            return new LineaRecetaResponse(r.getIngredienteId(), r.getIngredienteNombre(), r.getUnidad(),
                    r.getCantidad());
        }
    }

    public record AlertaResponse(Long id, Long ingredienteId, String ingredienteNombre, String mensaje,
                                 PrioridadAlerta prioridad, boolean atendida, LocalDateTime fechaCreacion,
                                 LocalDateTime fechaAtencion) {
        public static AlertaResponse desde(Alerta a) {
            return new AlertaResponse(a.getId(), a.getIngredienteId(), a.getIngredienteNombre(), a.getMensaje(),
                    a.getPrioridad(), a.isAtendida(), a.getFechaCreacion(), a.getFechaAtencion());
        }
    }
}
