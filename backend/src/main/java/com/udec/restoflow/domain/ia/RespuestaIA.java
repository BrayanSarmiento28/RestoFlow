package com.udec.restoflow.domain.ia;

import java.time.LocalDateTime;

/**
 * Un texto de RestoFlow AI y de dónde salió: escrito por la IA externa (Gemini) o armado con las reglas porque la
 * IA no respondió. La pantalla lo indica para que el usuario sepa qué está leyendo.
 */
public record RespuestaIA(String texto, Origen origen, LocalDateTime generadoEn) {

    public enum Origen { GEMINI, REGLAS }
}
