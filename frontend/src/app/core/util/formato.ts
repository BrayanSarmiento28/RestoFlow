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

/** Fecha local en formato AAAA-MM-DD (la que espera el API), sin el desfase de toISOString(). */
export function fechaIso(fecha: Date): string {
  const d = new Date(fecha.getTime() - fecha.getTimezoneOffset() * 60000);
  return d.toISOString().slice(0, 10);
}

/** 1.250,5 → hasta 2 decimales, con separadores de Colombia. */
export function numero(valor: number | null | undefined, decimales = 2): string {
  return (valor ?? 0).toLocaleString('es-CO', { maximumFractionDigits: decimales });
}

/** 15 oct, 2:35 p. m. */
export function fechaHora(fecha: string | null | undefined): string {
  return fecha
    ? new Date(fecha).toLocaleString('es-CO', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
    : '';
}
