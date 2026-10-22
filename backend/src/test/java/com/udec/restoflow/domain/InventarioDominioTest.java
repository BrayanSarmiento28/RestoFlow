package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.TipoMovimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-05 a RF-07 · Reglas de alertas, movimientos y recetas. */
class InventarioDominioTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 12, 0);

    @Test
    @DisplayName("Una alerta nueva queda pendiente; al atenderla guarda la hora una sola vez")
    void alerta() {
        Alerta a = Alerta.nueva(1L, "  Quedan 2 und de Huevo  ", PrioridadAlerta.MEDIA, AHORA);
        assertThat(a.isAtendida()).isFalse();
        assertThat(a.getMensaje()).isEqualTo("Quedan 2 und de Huevo");

        a.marcarAtendida(AHORA.plusHours(1));
        a.marcarAtendida(AHORA.plusHours(5));     // atender otra vez no cambia la hora

        assertThat(a.isAtendida()).isTrue();
        assertThat(a.getFechaAtencion()).isEqualTo(AHORA.plusHours(1));
    }

    @Test
    @DisplayName("Una alerta necesita mensaje y prioridad")
    void alertaInvalida() {
        assertThatThrownBy(() -> Alerta.nueva(1L, " ", PrioridadAlerta.ALTA, AHORA)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Alerta.nueva(1L, "Se agotó", null, AHORA)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Un movimiento guarda el motivo sin espacios y vacío como null")
    void movimiento() {
        MovimientoInventario m = MovimientoInventario.registrar(1L, TipoMovimiento.ENTRADA, new BigDecimal("30"),
                "  Compra semanal ", 7L, null, AHORA);
        assertThat(m.getMotivo()).isEqualTo("Compra semanal");
        MovimientoInventario sinMotivo = MovimientoInventario.registrar(1L, TipoMovimiento.SALIDA, BigDecimal.ONE,
                "   ", null, 15L, AHORA);
        assertThat(sinMotivo.getMotivo()).isNull();
        assertThat(sinMotivo.getPedidoId()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Un movimiento no admite cantidad negativa, motivo de más de 200 letras ni datos faltantes")
    void movimientoInvalido() {
        assertThatThrownBy(() -> MovimientoInventario.registrar(1L, TipoMovimiento.SALIDA, new BigDecimal("-1"), null,
                null, null, AHORA)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> MovimientoInventario.registrar(1L, TipoMovimiento.SALIDA, BigDecimal.ONE, "x".repeat(201),
                null, null, AHORA)).isInstanceOf(DomainException.class).hasMessageContaining("200");
        assertThatThrownBy(() -> MovimientoInventario.registrar(null, TipoMovimiento.SALIDA, BigDecimal.ONE, null,
                null, null, AHORA)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("La receta calcula el consumo: 150 g por plato × 3 platos = 450 g")
    void consumoReceta() {
        RecetaIngrediente arroz = new RecetaIngrediente(null, 1L, 2L, new BigDecimal("150"));
        assertThat(arroz.consumoPara(3)).isEqualByComparingTo("450");
    }

    @Test
    @DisplayName("Una línea de receta necesita producto, ingrediente y una cantidad mayor que cero")
    void recetaInvalida() {
        assertThatThrownBy(() -> new RecetaIngrediente(null, 1L, 2L, BigDecimal.ZERO)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RecetaIngrediente(null, null, 2L, BigDecimal.ONE)).isInstanceOf(DomainException.class);
    }
}
