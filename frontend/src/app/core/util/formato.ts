/** $ 32.000 (pesos colombianos, sin decimales). */
export function pesos(valor: number | null | undefined): string {
  return '$ ' + Math.round(valor ?? 0).toLocaleString('es-CO');
}

/** 2:35 p. m. */
export function hora(fecha: string | null | undefined): string {
  return fecha ? new Date(fecha).toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' }) : '';
}

/** Minutos completos que han pasado desde la fecha hasta "ahora". */
export function minutosDesde(fecha: string | null | undefined, ahora: number): number {
  return fecha ? Math.max(0, Math.floor((ahora - new Date(fecha).getTime()) / 60000)) : 0;
}
