// RF-05 · Código de barras de un insumo (para buscarlo en Open Food Facts).
// Funciones puras: se prueban con Jest (codigo-barras.test.ts).

/** Largos que existen: EAN-8, UPC-A (12), EAN-13 y GTIN-14. */
const LARGOS_VALIDOS = new Set([8, 12, 13, 14]);

/** Deja solo los números: "7702 011-012345" → "7702011012345". */
export function limpiarCodigo(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[\s-]/g, '');
}

/** Tiene solo números y un largo de código de barras real (se puede buscar). */
export function codigoCompleto(texto: string | null | undefined): boolean {
  const codigo = limpiarCodigo(texto);
  return /^\d+$/.test(codigo) && LARGOS_VALIDOS.has(codigo.length);
}

/**
 * Revisa el último número (dígito de control) con el algoritmo GS1: de derecha a izquierda, sin contar el
 * dígito de control, las posiciones impares valen ×3 y las pares ×1; el dígito de control completa la decena.
 * Sirve para avisar al usuario si escribió mal un número antes de buscar.
 */
export function digitoDeControlValido(texto: string | null | undefined): boolean {
  if (!codigoCompleto(texto)) return false;
  const digitos = limpiarCodigo(texto).split('').map(Number);
  const control = digitos.pop()!;
  digitos.reverse();
  const suma = digitos.reduce((total, d, i) => total + d * (i % 2 === 0 ? 3 : 1), 0);
  return (10 - (suma % 10)) % 10 === control;
}
