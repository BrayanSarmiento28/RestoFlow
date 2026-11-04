package com.udec.restoflow.infrastructure.logging;

import com.udec.restoflow.application.port.in.RegistrarPeticionUseCase;
import com.udec.restoflow.domain.model.RegistroPeticion;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Guarda las peticiones en la base de datos <b>en segundo plano</b>: la respuesta al usuario no espera a que se
 * escriba el registro, así guardar el log no hace más lenta la aplicación.
 * <ul>
 *   <li>Un solo hilo y una cola de hasta 5.000 registros. Si la base de datos se cae o va muy lenta y la cola se
 *       llena, los registros que no caben se descartan (y se avisa en el log de texto): el restaurante nunca se
 *       detiene por culpa del registro.</li>
 *   <li>Si guardar falla, queda un WARN en el log de texto, que sigue funcionando aunque la base de datos no.</li>
 *   <li>Se apaga con {@code REGISTRO_BD_HABILITADO=false}.</li>
 * </ul>
 */
@Component
public class EscritorRegistroAsincrono implements DestinoRegistro {

    private static final Logger log = LoggerFactory.getLogger("restoflow.registro");
    private static final int CAPACIDAD_COLA = 5000;

    /** Se busca al primer uso: así el filtro (que se crea muy temprano) no obliga a crear antes la base de datos. */
    private final ObjectProvider<RegistrarPeticionUseCase> registrar;
    private final boolean habilitado;
    private final ThreadPoolExecutor hilo;
    private final AtomicLong descartados = new AtomicLong();

    public EscritorRegistroAsincrono(ObjectProvider<RegistrarPeticionUseCase> registrar,
                                     @Value("${restoflow.registro.habilitado:true}") boolean habilitado) {
        this.registrar = registrar;
        this.habilitado = habilitado;
        this.hilo = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(CAPACIDAD_COLA),
                tarea -> {
                    Thread t = new Thread(tarea, "registro-bd");
                    t.setDaemon(true);
                    return t;
                });
    }

    @Override
    public void enviar(RegistroPeticion registro) {
        if (!habilitado) {
            return;
        }
        try {
            hilo.execute(() -> guardar(registro));
        } catch (RejectedExecutionException e) {
            long total = descartados.incrementAndGet();
            if (total == 1 || total % 1000 == 0) {
                log.warn("La cola del registro en base de datos está llena: {} registros descartados", total);
            }
        }
    }

    private void guardar(RegistroPeticion registro) {
        try {
            registrar.getObject().registrar(registro);
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar el registro {} {} en la base de datos: {}",
                    registro.metodo(), registro.ruta(), e.getMessage());
        }
    }

    /** Al apagar el backend se terminan de escribir los registros pendientes (máximo 5 segundos). */
    @PreDestroy
    void apagar() throws InterruptedException {
        hilo.shutdown();
        if (!hilo.awaitTermination(5, TimeUnit.SECONDS)) {
            hilo.shutdownNow();
        }
    }

    /** Para las pruebas: cuántos registros esperan en la cola. */
    int pendientes() {
        return hilo.getQueue().size() + hilo.getActiveCount();
    }
}
