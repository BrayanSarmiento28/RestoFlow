package com.udec.restoflow.domain.model;

import java.util.List;

/**
 * Resumen del registro de actividad: cuántas peticiones, cuántas fallaron y qué tan rápido.
 *
 * @param rechazadas peticiones 4xx (datos inválidos, sin permiso, no existe...)
 * @param errores    peticiones 5xx (falló el servidor)
 * @param lentas     peticiones de más de 1 segundo
 * @param promedioMs duración promedio en milisegundos
 */
public record ResumenRegistro(long total, long rechazadas, long errores, long lentas, long promedioMs) {

    /** Una página de registros, del más reciente al más antiguo. */
    public record Pagina(List<RegistroPeticion> registros, long total, int pagina, int tamano) {

        public int totalPaginas() {
            return tamano == 0 ? 0 : (int) ((total + tamano - 1) / tamano);
        }
    }
}
