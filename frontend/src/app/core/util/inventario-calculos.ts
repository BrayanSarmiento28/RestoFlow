// RF-05 a RF-07 · Cálculos de la pantalla de inventario.
// Son funciones puras (sin Angular, sin HTTP): reciben datos y devuelven un resultado.
// Por eso se pueden probar con Jest de forma aislada (inventario-calculos.test.ts).
import { ABREVIATURA_UNIDAD } from '../entities/ingrediente';
import { numero } from './formato';
import type { Ingrediente } from '../entities/ingrediente';
import type { MovimientoInventario, TipoMovimiento } from '../entities/movimiento-inventario';

/** agotado → minimo → pronto (alcanza ≤ 3 días) → revisar (cerca del mínimo) → ok */
export type EstadoInsumo = 'agotado' | 'minimo' | 'pronto' | 'revisar' | 'ok';

/** Fila de la tabla: el insumo más lo que se calcula para mostrarlo. */
export interface FilaInsumo {
  insumo: Ingrediente;
  estado: EstadoInsumo;
  /** Días que alcanza el stock con el consumo promedio por ventas (null = sin ventas recientes). */
  dias: number | null;
  /** Ancho de la barra en %: el mínimo queda en la marca negra (33 %). */
  barra: number;
  icono: string | null;
}

/** Días de historial con los que se calcula el consumo promedio. */
export const DIAS_CONSUMO = 7;
/** Con este número de días o menos, el insumo "se acaba pronto". */
export const DIAS_POR_AGOTARSE = 3;
/** Hasta 1,5 veces el mínimo, el insumo queda "por revisar". */
export const MARGEN_REVISAR = 1.5;

const ORDEN_URGENCIA: EstadoInsumo[] = ['agotado', 'minimo', 'pronto', 'revisar', 'ok'];

/** Imagen según el tipo de insumo (por palabras del nombre); sin coincidencia se muestra la inicial. */
const ICONOS: [RegExp, string][] = [
  [/pollo|pechuga|carne|res|cerdo|chicharr|chorizo|tocino/i, 'carnes'],
  [/pescado|mojarra|trucha|camar|at[uú]n|salm[oó]n/i, 'pescados'],
  [/arroz|fr[ií]jol|lenteja|harina|ma[ií]z|pasta|garbanzo|az[uú]car|sal\b/i, 'granos'],
  [/leche|queso|crema|mantequilla|huevo|yogur/i, 'lacteos'],
  [/papa|yuca|mazorca|pl[aá]tano|tomate|cebolla|aguacate|lechuga|zanahoria|ajo|verdura/i, 'verduras'],
  [/lim[oó]n|naranja|mora|lulo|maracuy|pulpa|fruta|coco|fresa|mango/i, 'frutas'],
];

/**
 * Consumo promedio por día de cada insumo, contando solo las salidas generadas por ventas
 * (las que tienen pedido). Las mermas y los ajustes no cuentan como consumo.
 */
export function consumoDiarioPorVentas(
  movimientos: MovimientoInventario[],
  dias = DIAS_CONSUMO,
): Map<number, number> {
  const total = new Map<number, number>();
  movimientos
    .filter((m) => m.tipo === 'SALIDA' && m.pedidoId !== null)
    .forEach((m) => total.set(m.ingredienteId, (total.get(m.ingredienteId) ?? 0) + m.cantidad));
  return new Map([...total].map(([id, cantidad]) => [id, cantidad / dias]));
}

/** Días que alcanza el stock con ese consumo diario (null si no se ha vendido nada que lo use). */
export function diasRestantes(stockActual: number, consumoDiario: number): number | null {
  return consumoDiario > 0 ? stockActual / consumoDiario : null;
}

export function estadoInsumo(insumo: Ingrediente, dias: number | null): EstadoInsumo {
  if (insumo.agotado) return 'agotado';
  if (insumo.enMinimo) return 'minimo';
  if (dias !== null && dias <= DIAS_POR_AGOTARSE) return 'pronto';
  if (insumo.stockActual <= insumo.stockMinimo * MARGEN_REVISAR) return 'revisar';
  return 'ok';
}

/** Ancho de la barra: la escala llega hasta 3 veces el mínimo (así el mínimo queda en el 33 %). */
export function anchoBarra(stockActual: number, stockMinimo: number): number {
  const escala = stockMinimo > 0 ? stockMinimo * 3 : Math.max(stockActual, 1);
  return Math.min(100, (stockActual / escala) * 100);
}

export function iconoInsumo(nombre: string): string | null {
  const icono = ICONOS.find(([patron]) => patron.test(nombre))?.[1];
  return icono ? `img/insumos/${icono}.svg` : null;
}

export function filaInsumo(insumo: Ingrediente, consumoDiario: Map<number, number>): FilaInsumo {
  const dias = diasRestantes(insumo.stockActual, consumoDiario.get(insumo.id) ?? 0);
  return {
    insumo,
    estado: estadoInsumo(insumo, dias),
    dias,
    barra: anchoBarra(insumo.stockActual, insumo.stockMinimo),
    icono: iconoInsumo(insumo.nombre),
  };
}

/** Filas de los insumos activos, primero los más urgentes y luego en orden alfabético. */
export function filasOrdenadas(insumos: Ingrediente[], movimientos: MovimientoInventario[]): FilaInsumo[] {
  const consumo = consumoDiarioPorVentas(movimientos);
  return insumos
    .filter((i) => i.activo)
    .map((i) => filaInsumo(i, consumo))
    .sort((a, b) => urgencia(a.estado) - urgencia(b.estado) || a.insumo.nombre.localeCompare(b.insumo.nombre));
}

export function urgencia(estado: EstadoInsumo): number {
  return ORDEN_URGENCIA.indexOf(estado);
}

/** "Arroz, Huevo y 2 más" */
export function resumirNombres(nombres: string[]): string {
  return nombres.length <= 3 ? nombres.join(', ') : `${nombres.slice(0, 2).join(', ')} y ${nombres.length - 2} más`;
}

/** Signo con el que se muestra cada tipo de movimiento. */
const SIGNO_MOVIMIENTO: Record<TipoMovimiento, string> = { ENTRADA: '+', SALIDA: '−', AJUSTE: '= ' };

/** "+30 und" (entrada), "−6 und" (salida), "= 12 kg" (ajuste al valor contado). */
export function cantidadMovimiento(m: Pick<MovimientoInventario, 'tipo' | 'cantidad' | 'unidad'>): string {
  return `${SIGNO_MOVIMIENTO[m.tipo]}${numero(m.cantidad)} ${ABREVIATURA_UNIDAD[m.unidad]}`;
}
