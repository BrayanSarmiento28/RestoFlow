package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.FiltroRegistro;
import com.udec.restoflow.domain.model.ResumenRegistro;

/** Registro de actividad (GET /api/registro, solo ADMIN): buscar peticiones y ver el resumen del periodo. */
public interface ConsultarRegistroUseCase {

    /** Página por defecto: 50 registros; máximo 200. */
    int TAMANO_POR_DEFECTO = 50;
    int TAMANO_MAXIMO = 200;

    ResultadoRegistro consultar(FiltroRegistro filtro, int pagina, int tamano);

    record ResultadoRegistro(ResumenRegistro resumen, ResumenRegistro.Pagina pagina) { }
}
