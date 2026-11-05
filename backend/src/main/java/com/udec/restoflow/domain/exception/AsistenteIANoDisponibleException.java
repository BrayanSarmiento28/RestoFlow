package com.udec.restoflow.domain.exception;

/**
 * La IA externa (Gemini) no respondió, respondió con error o no tiene clave configurada. Se devuelve como 503.
 * Donde se puede, RestoFlow sigue funcionando sin ella (con las reglas); solo el chat la necesita sí o sí.
 */
public class AsistenteIANoDisponibleException extends ServicioExternoNoDisponibleException {

    public static final String SIN_CLAVE = "RestoFlow AI no tiene configurada la clave de Gemini (GEMINI_API_KEY). "
            + "Pida al administrador del sistema que la agregue.";
    public static final String NO_RESPONDE = "RestoFlow AI (Gemini) no está disponible en este momento. "
            + "Intente de nuevo en un minuto.";

    public AsistenteIANoDisponibleException(String mensaje) {
        super(mensaje, null);
    }

    public AsistenteIANoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
