package com.udec.restoflow.infrastructure.logging;

import com.udec.restoflow.application.port.in.RegistrarPeticionUseCase;
import com.udec.restoflow.domain.model.RegistroPeticion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El registro de actividad se guarda en un hilo aparte, sin frenar la respuesta ni tumbar el API si falla. */
class EscritorRegistroAsincronoTest {

    private final RegistrarPeticionUseCase registrar = mock(RegistrarPeticionUseCase.class);

    @SuppressWarnings("unchecked")
    private EscritorRegistroAsincrono escritor(boolean habilitado) {
        ObjectProvider<RegistrarPeticionUseCase> proveedor = mock(ObjectProvider.class);
        when(proveedor.getObject()).thenReturn(registrar);
        return new EscritorRegistroAsincrono(proveedor, habilitado);
    }

    private static RegistroPeticion registro() {
        return RegistroPeticion.nuevo(LocalDateTime.of(2026, 11, 12, 12, 0), "abc123", "admin@pruebas.com", "ADMIN",
                "GET", "/api/pedidos", 200, 15);
    }

    @Test
    @DisplayName("Guarda el registro en segundo plano")
    void guarda() throws InterruptedException {
        EscritorRegistroAsincrono escritor = escritor(true);

        escritor.enviar(registro());

        verify(registrar, timeout(2000)).registrar(any());
        escritor.apagar();
        assertThat(escritor.pendientes()).isZero();
    }

    @Test
    @DisplayName("Deshabilitado (restoflow.registro.habilitado=false) no guarda nada")
    void deshabilitado() throws InterruptedException {
        EscritorRegistroAsincrono escritor = escritor(false);

        escritor.enviar(registro());
        escritor.apagar();

        verify(registrar, never()).registrar(any());
    }

    @Test
    @DisplayName("Si la base de datos falla, el error queda en el log y los siguientes registros se siguen guardando")
    void falloDeLaBase() throws InterruptedException {
        doThrow(new IllegalStateException("sin conexión")).doNothing().when(registrar).registrar(any());
        EscritorRegistroAsincrono escritor = escritor(true);

        escritor.enviar(registro());
        escritor.enviar(registro());

        verify(registrar, timeout(2000).times(2)).registrar(any());
        escritor.apagar();
    }

    @Test
    @DisplayName("Con la cola llena se descartan registros en vez de frenar al API; al apagar no se espera más de 5 s")
    void colaLlena() throws InterruptedException {
        CountDownLatch bloqueo = new CountDownLatch(1);
        doAnswer(invocacion -> {
            bloqueo.await(30, TimeUnit.SECONDS);   // la base de datos "no responde"
            return null;
        }).when(registrar).registrar(any());
        EscritorRegistroAsincrono escritor = escritor(true);

        for (int i = 0; i < 6100; i++) {           // 1 en curso + 5000 en cola + 1099 descartados
            escritor.enviar(registro());
        }
        assertThat(escritor.pendientes()).isGreaterThanOrEqualTo(5000);

        escritor.apagar();                          // espera 5 s y luego interrumpe el hilo bloqueado
        verify(registrar, times(1)).registrar(any());
    }
}
