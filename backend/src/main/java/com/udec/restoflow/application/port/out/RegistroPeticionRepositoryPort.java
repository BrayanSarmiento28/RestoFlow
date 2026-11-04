package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.FiltroRegistro;
import com.udec.restoflow.domain.model.RegistroPeticion;
import com.udec.restoflow.domain.model.ResumenRegistro;

import java.time.LocalDateTime;

/** Registro de actividad en la base de datos (tabla "registro_peticiones"). */
public interface RegistroPeticionRepositoryPort {

    void guardar(RegistroPeticion registro);

    /** Del más reciente al más antiguo. */
    ResumenRegistro.Pagina buscar(FiltroRegistro filtro, int pagina, int tamano);

    ResumenRegistro resumir(FiltroRegistro filtro);

    /** Borra los registros anteriores a esa fecha y devuelve cuántos borró. */
    int borrarAnterioresA(LocalDateTime limite);
}
