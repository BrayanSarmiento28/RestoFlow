// RF-08 · Cálculos de la gráfica "Ventas por hora" y de la comparación con ayer del Dashboard.
// Funciones puras: se prueban con Jest (graficas.test.ts).

export interface VentaHora {
  hora: number;
  total: number;
  pedidos: number;
}

export interface ColumnaHora extends VentaHora {
  /** Altura de la barra en % respecto a la hora con más ventas. */
  alto: number;
  /** Es la hora en curso (se pinta de otro color). */
  actual: boolean;
}

/** Columnas mínimas de la gráfica, para que no quede una sola barra muy ancha. */
export const MINIMO_COLUMNAS = 6;

/**
 * Columnas de la gráfica: desde la primera hora con ventas hasta la hora actual, sin huecos
 * (una hora sin ventas aparece con total 0), con al menos 6 columnas.
 */
export function columnasPorHora(ventas: VentaHora[], horaActual: number): ColumnaHora[] {
  if (ventas.length === 0) return [];
  const primera = Math.min(...ventas.map((v) => v.hora));
  const ultima = Math.max(horaActual, ...ventas.map((v) => v.hora));
  const desde = Math.max(0, Math.min(primera, ultima - (MINIMO_COLUMNAS - 1)));
  const maximo = Math.max(...ventas.map((v) => v.total), 1);
  return Array.from({ length: ultima - desde + 1 }, (_, i) => {
    const hora = desde + i;
    const v = ventas.find((x) => x.hora === hora);
    const total = v?.total ?? 0;
    return { hora, total, pedidos: v?.pedidos ?? 0, alto: (total / maximo) * 100, actual: hora === horaActual };
  });
}

/** Variación porcentual redondeada: 120.000 frente a 100.000 → 20. Sin dato anterior → null. */
export function variacionPorcentual(actual: number, anterior: number): number | null {
  if (!anterior) return null;
  return Math.round(((actual - anterior) / anterior) * 100);
}
