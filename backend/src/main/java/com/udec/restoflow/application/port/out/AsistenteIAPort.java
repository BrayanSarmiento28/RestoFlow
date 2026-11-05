package com.udec.restoflow.application.port.out;

/**
 * Puerto de salida hacia una IA externa que escribe texto (hoy Google Gemini). Si mañana se quiere otra IA
 * (Groq, OpenAI, Claude...), se crea otro adaptador con este mismo puerto y no cambia nada más (SOLID - O y D).
 */
public interface AsistenteIAPort {

    /** false si no hay clave configurada: no vale la pena llamar, se usa directamente el plan B. */
    boolean configurado();

    /**
     * @param instrucciones cómo debe comportarse la IA (rol, tono, formato)
     * @param mensaje       los datos y lo que se le pide
     * @return el texto que respondió
     * @throws com.udec.restoflow.domain.exception.AsistenteIANoDisponibleException si no responde o falla
     */
    String generar(String instrucciones, String mensaje);
}
