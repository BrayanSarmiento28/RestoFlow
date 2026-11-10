package com.udec.restoflow.application.port.in;

/**
 * RES-40 · Alertas automáticas de RestoFlow AI. Una tarea programada lo ejecuta cada hora; también se puede pedir
 * a mano desde la pantalla de RestoFlow AI.
 * <ul>
 *   <li>Crea una alerta cuando un insumo, al ritmo actual, alcanza para 3 días o menos.</li>
 *   <li>Cierra sola esa alerta cuando el insumo vuelve a alcanzar para más tiempo (por ejemplo, tras una compra).</li>
 *   <li>No repite el aviso si el insumo ya tiene una alerta pendiente (de RestoFlow AI o de stock).</li>
 * </ul>
 */
public interface GenerarAlertasIAUseCase {

    ResultadoAlertas generarAlertas();

    /**
     * @param yaAvisados insumos que se acaban pronto pero ya tenían una alerta pendiente (no se repite el aviso)
     */
    record ResultadoAlertas(int creadas, int cerradas, int yaAvisados) { }
}
