package com.udec.restoflow.application.service;

import com.udec.restoflow.domain.exception.RecursoNoEncontradoException;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.UnidadMedida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-07 · Cuándo se crean y se cierran las alertas (ControlAlertas) y cómo se atienden (GestionarAlertasService). */
class AlertasServiceTest {

    private DoblesInventario.AlertasEnMemoria alertas;
    private Dobles.RelojManual reloj;
    private ControlAlertas control;
    private GestionarAlertasService servicio;

    @BeforeEach
    void preparar() {
        alertas = new DoblesInventario.AlertasEnMemoria();
        reloj = new Dobles.RelojManual();
        control = new ControlAlertas(alertas, reloj);
        servicio = new GestionarAlertasService(alertas, reloj);
    }

    private static Ingrediente huevo(String stock) {
        return new Ingrediente(1L, "Huevo", UnidadMedida.UNIDAD, new BigDecimal(stock), new BigDecimal("6"), true);
    }

    @Test
    @DisplayName("Por encima del mínimo no se crea ninguna alerta")
    void sinAlerta() {
        control.revisar(huevo("20"));
        assertThat(alertas.datos).isEmpty();
    }

    @Test
    @DisplayName("Al llegar al mínimo se crea una alerta MEDIA con la cantidad y la unidad")
    void alertaMedia() {
        control.revisar(huevo("6"));
        assertThat(alertas.pendientes()).singleElement().satisfies(a -> {
            assertThat(a.getPrioridad()).isEqualTo(PrioridadAlerta.MEDIA);
            assertThat(a.getMensaje()).isEqualTo("Quedan 6 und de Huevo (mínimo 6 und).");
        });
    }

    @Test
    @DisplayName("Revisar varias veces no repite la alerta")
    void sinDuplicados() {
        control.revisar(huevo("5"));
        control.revisar(huevo("4"));
        assertThat(alertas.pendientes()).hasSize(1);
    }

    @Test
    @DisplayName("Al agotarse, la ALTA reemplaza a la MEDIA pendiente")
    void agotado() {
        control.revisar(huevo("5"));
        control.revisar(huevo("0"));
        assertThat(alertas.pendientes()).singleElement().satisfies(a -> {
            assertThat(a.getPrioridad()).isEqualTo(PrioridadAlerta.ALTA);
            assertThat(a.getMensaje()).startsWith("Se agotó Huevo");
        });
        assertThat(alertas.datos).hasSize(2);
    }

    @Test
    @DisplayName("Si ya hay una ALTA pendiente, bajar al mínimo no crea una MEDIA")
    void altaPrevalece() {
        control.revisar(huevo("0"));
        control.revisar(huevo("3"));
        assertThat(alertas.pendientes()).extracting(Alerta::getPrioridad).containsExactly(PrioridadAlerta.ALTA);
    }

    @Test
    @DisplayName("Al reabastecer, las alertas pendientes se cierran solas")
    void reabastecer() {
        control.revisar(huevo("2"));
        reloj.avanzarMinutos(30);
        control.revisar(huevo("40"));
        assertThat(alertas.pendientes()).isEmpty();
        assertThat(alertas.datos.values()).allMatch(Alerta::isAtendida);
    }

    @Test
    @DisplayName("Un faltante en una venta crea una alerta ALTA que pide un conteo físico")
    void faltante() {
        control.faltante(huevo("0"), new BigDecimal("2"), 15L);
        assertThat(alertas.pendientes()).singleElement().satisfies(a -> {
            assertThat(a.getPrioridad()).isEqualTo(PrioridadAlerta.ALTA);
            assertThat(a.getMensaje()).contains("pedido #15").contains("2 und").contains("conteo físico");
        });
    }

    @Test
    @DisplayName("El personal atiende una alerta manualmente y deja de estar pendiente")
    void atender() {
        control.revisar(huevo("3"));
        Long id = alertas.pendientes().get(0).getId();
        reloj.avanzarMinutos(10);

        Alerta atendida = servicio.atender(id);

        assertThat(atendida.isAtendida()).isTrue();
        assertThat(atendida.getFechaAtencion()).isNotNull();
        assertThat(servicio.listar(true)).isEmpty();
        assertThat(servicio.listar(false)).hasSize(1);
        assertThatThrownBy(() -> servicio.atender(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
