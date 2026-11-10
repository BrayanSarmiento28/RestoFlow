// RestoFlow AI con IA externa (Google Gemini) · lógica de pantalla sin Angular (se prueba con Jest).
import type { OrigenIA } from '../models/restoflow-ai.models';

export const PREGUNTA_MAXIMA = 500;

/** Ideas para empezar (se pueden tocar en vez de escribir). */
export const SUGERENCIAS = [
  '¿Qué debo comprar esta semana?',
  '¿Cómo me fue en ventas frente a la semana pasada?',
  '¿Qué debo preparar para mañana?',
];

/** De dónde salió el texto, para que el usuario sepa qué está leyendo. */
export function textoOrigen(origen: OrigenIA | null | undefined): string {
  if (origen === 'GEMINI') return 'Redactado por Gemini';
  if (origen === 'REGLAS') return 'Calculado con reglas (Gemini no disponible)';
  return '';
}

/** Se puede enviar si hay texto, no pasa del máximo y no hay otra pregunta en camino. */
export function puedePreguntar(texto: string, enviando: boolean): boolean {
  const limpio = texto.trim();
  return !enviando && limpio.length > 0 && limpio.length <= PREGUNTA_MAXIMA;
}

export interface Intercambio {
  pregunta: string;
  respuesta: string | null;     // null = la IA no pudo responder
  error: string | null;
  hora: string;
}

/** La conversación de la pantalla: la más reciente arriba y como máximo 10 (no se guarda en ningún lado). */
export function agregarIntercambio(lista: Intercambio[], nuevo: Intercambio, maximo = 10): Intercambio[] {
  return [nuevo, ...lista].slice(0, maximo);
}

/** "12 / 500" para el contador del cuadro de texto. */
export function contador(texto: string): string {
  return `${texto.trim().length} / ${PREGUNTA_MAXIMA}`;
}
