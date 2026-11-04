package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarRegistroUseCase;
import com.udec.restoflow.application.port.in.LimpiarRegistroUseCase;
import com.udec.restoflow.application.port.in.RegistrarPeticionUseCase;
import com.udec.restoflow.application.port.out.RegistroPeticionRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.FiltroRegistro;
import com.udec.restoflow.domain.model.RegistroPeticion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Registro de actividad en la base de datos (recomendación del comité). Tres casos de uso:
 * <ul>
 *   <li>Guardar cada petición al API (en segundo plano, para no hacer más lenta la respuesta).</li>
 *   <li>Consultarlas con filtros y un resumen (pantalla del administrador).</li>
 *   <li>Borrar las de más de N días (por defecto 30) para que la tabla no crezca sin límite.</li>
 * </ul>
 * Los logs de texto (consola y archivos) siguen igual: la base de datos es una fuente más, fácil de consultar
 * desde la aplicación. Las dos comparten el id de petición.
 */
@Transactional
public class RegistroActividadService implements RegistrarPeticionUseCase, ConsultarRegistroUseCase,
        LimpiarRegistroUseCase {

    private static final Logger log = LoggerFactory.getLogger("restoflow.registro");

    private final RegistroPeticionRepositoryPort registros;
    private final Clock reloj;
    private final int diasConservacion;

    public RegistroActividadService(RegistroPeticionRepositoryPort registros, Clock reloj, int diasConservacion) {
        if (diasConservacion < 1) {
            throw new DomainException("Los registros se deben conservar al menos 1 día");
        }
        this.registros = registros;
        this.reloj = reloj;
        this.diasConservacion = diasConservacion;
    }

    @Override
    public void registrar(RegistroPeticion registro) {
        registros.guardar(registro);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoRegistro consultar(FiltroRegistro filtro, int pagina, int tamano) {
        if (pagina < 0) {
            throw new DomainException("La página no puede ser negativa");
        }
        int tamanoValido = tamano <= 0 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
        return new ResultadoRegistro(registros.resumir(filtro), registros.buscar(filtro, pagina, tamanoValido));
    }

    @Override
    public int limpiar() {
        LocalDateTime limite = LocalDateTime.now(reloj).minusDays(diasConservacion);
        int borrados = registros.borrarAnterioresA(limite);
        log.info("evento=REGISTRO_LIMPIADO borrados={} anterioresA={}", borrados, limite.toLocalDate());
        return borrados;
    }

    public int getDiasConservacion() {
        return diasConservacion;
    }
}
