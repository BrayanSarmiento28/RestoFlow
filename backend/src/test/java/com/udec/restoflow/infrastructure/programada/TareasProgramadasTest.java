package com.udec.restoflow.infrastructure.programada;

import com.udec.restoflow.application.port.in.GenerarAlertasIAUseCase;
import com.udec.restoflow.application.port.in.GenerarAlertasIAUseCase.ResultadoAlertas;
import com.udec.restoflow.application.port.in.LimpiarRegistroUseCase;
import com.udec.restoflow.infrastructure.adapter.in.programada.LimpiezaRegistroProgramada;
import com.udec.restoflow.infrastructure.adapter.in.programada.RestoFlowAIProgramado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las tareas programadas llaman a su caso de uso y, si este falla, no se detienen (la siguiente vuelve a correr). */
class TareasProgramadasTest {

    @Test
    @DisplayName("RestoFlow AI revisa el inventario cada hora; un fallo queda en el log y no detiene la tarea")
    void revisionDelInventario() {
        GenerarAlertasIAUseCase alertas = mock(GenerarAlertasIAUseCase.class);
        when(alertas.generarAlertas()).thenReturn(new ResultadoAlertas(2, 1, 0))
                .thenThrow(new IllegalStateException("base de datos ocupada"));
        RestoFlowAIProgramado tarea = new RestoFlowAIProgramado(alertas);

        tarea.revisarInventario();
        assertThatCode(tarea::revisarInventario).doesNotThrowAnyException();

        verify(alertas, times(2)).generarAlertas();
    }

    @Test
    @DisplayName("La limpieza del registro de actividad corre cada noche; un fallo no detiene la tarea")
    void limpiezaDelRegistro() {
        LimpiarRegistroUseCase limpiar = mock(LimpiarRegistroUseCase.class);
        when(limpiar.limpiar()).thenReturn(120).thenThrow(new IllegalStateException("base de datos ocupada"));
        LimpiezaRegistroProgramada tarea = new LimpiezaRegistroProgramada(limpiar);

        tarea.limpiar();
        assertThatCode(tarea::limpiar).doesNotThrowAnyException();

        verify(limpiar, times(2)).limpiar();
    }
}
