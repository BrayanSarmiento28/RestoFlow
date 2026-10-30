// RestoFlow AI (Sprint 5) · lógica de presentación del análisis, sin Angular, para probarla con Jest.
// Los cálculos del análisis (consumo, tendencias, predicción) están en el backend (domain/ia); aquí solo se
// decide cómo mostrarlos.
import type { PrioridadAlerta } from '../entities/alerta';
import type {
  DiaSemana, PronosticoInsumo, Recomendacion, TipoRecomendacion, VentaDia,
} from '../models/restoflow-ai.models';

export const NOMBRE_DIA: Record<DiaSemana, string> = {
  MONDAY: 'lunes', TUESDAY: 'martes', WEDNESDAY: 'miércoles', THURSDAY: 'jueves',
  FRIDAY: 'viernes', SATURDAY: 'sábado', SUNDAY: 'domingo',
};

const DIA_CORTO: Record<DiaSemana, string> = {
  MONDAY: 'Lun', TUESDAY: 'Mar', WEDNESDAY: 'Mié', THURSDAY: 'Jue', FRIDAY: 'Vie', SATURDAY: 'Sáb', SUNDAY: 'Dom',
};

export const ETIQUETA_TIPO: Record<TipoRecomendacion, string> = {
  COMPRA: 'Compra', TENDENCIA: 'Tendencia', DEMANDA: 'Demanda', PRODUCTO: 'Producto', INFORMACION: 'Información',
};

export const NOMBRE_PRIORIDAD: Record<PrioridadAlerta, string> = { ALTA: 'Alta', MEDIA: 'Media', BAJA: 'Baja' };

/** Clase del chip de cada prioridad (los colores ya existen en styles.scss). */
export const CLASE_PRIORIDAD: Record<PrioridadAlerta, string> = {
  ALTA: 'rf-chip--alerta', MEDIA: 'rf-chip--aviso', BAJA: 'rf-chip--neutro',
};

/** Un cambio de menos de 5 % se considera estable. */
const UMBRAL_ESTABLE = 5;

/** "+28 %", "-14 %", "0 %" o "Sin datos" si no hay semana anterior con qué comparar. */
export function textoTendencia(porcentaje: number | null): string {
  if (porcentaje === null) return 'Sin datos';
  return (porcentaje > 0 ? '+' : '') + porcentaje + ' %';
}

/** Para elegir la flecha y el color de una tendencia. */
export function direccion(porcentaje: number | null): 'sube' | 'baja' | 'estable' {
  if (porcentaje === null || Math.abs(porcentaje) < UMBRAL_ESTABLE) return 'estable';
  return porcentaje > 0 ? 'sube' : 'baja';
}

/** "1 día", "2,5 días" o "Sin consumo" cuando no se ha usado en la semana. */
export function textoCobertura(dias: number | null): string {
  if (dias === null) return 'Sin consumo';
  const texto = dias.toLocaleString('es-CO', { maximumFractionDigits: 1 });
  return dias === 1 ? '1 día' : texto + ' días';
}

/** Hora del día como se dice en Colombia: 9 a. m., 12 m., 2 p. m. */
export function textoHora(hora: number | null): string {
  if (hora === null) return 'Sin datos';
  if (hora === 0) return '12 a. m.';
  if (hora === 12) return '12 m.';
  return hora < 12 ? `${hora} a. m.` : `${hora - 12} p. m.`;
}

/** Primera letra en mayúscula y el resto igual: "sábado, 31 de octubre" → "Sábado, 31 de octubre". */
export function capitalizar(texto: string): string {
  return texto ? texto.charAt(0).toUpperCase() + texto.slice(1) : texto;
}

/** "Sábado, 31 de octubre", a partir de AAAA-MM-DD (sin el desfase de zona horaria de new Date(texto)). */
export function fechaLarga(iso: string): string {
  const [anio, mes, dia] = iso.split('-').map(Number);
  return capitalizar(new Date(anio, mes - 1, dia)
    .toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' }));
}

/** El mensaje después de "Generar alertas ahora", explicando por qué puede no haber alertas nuevas. */
export function mensajeAlertas(r: { creadas: number; cerradas: number; yaAvisados: number }): string {
  const partes = [
    `${r.creadas} ${r.creadas === 1 ? 'alerta nueva' : 'alertas nuevas'}`,
    `${r.cerradas} ${r.cerradas === 1 ? 'cerrada' : 'cerradas'}`,
  ];
  let texto = `RestoFlow AI revisó el inventario: ${partes.join(' y ')}.`;
  if (r.yaAvisados > 0) {
    texto += r.yaAvisados === 1
      ? ' 1 insumo que se acaba pronto ya tenía una alerta pendiente, así que no se repitió.'
      : ` ${r.yaAvisados} insumos que se acaban pronto ya tenían una alerta pendiente, así que no se repitieron.`;
  }
  return texto + ' Las alertas se ven en Inventario.';
}

export interface ColumnaDia {
  dia: DiaSemana;
  etiqueta: string;
  promedio: number;
  /** Alto de la barra en %, respecto al día con más ventas. */
  alto: number;
  /** Es el día de más ventas. */
  fuerte: boolean;
}

/** Columnas del gráfico "Ventas promedio por día" (de lunes a domingo). */
export function columnasPorDia(dias: VentaDia[]): ColumnaDia[] {
  const maximo = Math.max(0, ...dias.map((d) => d.promedio));
  return dias.map((d) => ({
    dia: d.dia,
    etiqueta: DIA_CORTO[d.dia],
    promedio: d.promedio,
    alto: maximo > 0 ? Math.round((d.promedio / maximo) * 100) : 0,
    fuerte: maximo > 0 && d.promedio === maximo,
  }));
}

/** Cuántas recomendaciones hay de cada prioridad (para el resumen de arriba). */
export function contarPorPrioridad(recomendaciones: Recomendacion[]): Record<PrioridadAlerta, number> {
  const cuenta: Record<PrioridadAlerta, number> = { ALTA: 0, MEDIA: 0, BAJA: 0 };
  recomendaciones.forEach((r) => cuenta[r.prioridad]++);
  return cuenta;
}

/**
 * Las recomendaciones que se muestran en el Dashboard: las n más prioritarias que piden una acción
 * (las de información sobre el propio análisis quedan solo en la pantalla de RestoFlow AI).
 */
export function principales(recomendaciones: Recomendacion[], n = 3): Recomendacion[] {
  return recomendaciones.filter((r) => r.tipo !== 'INFORMACION').slice(0, n);
}

/** Insumos que RestoFlow AI recomienda comprar (los demás alcanzan para más de 7 días). */
export function insumosPorComprar(insumos: PronosticoInsumo[]): PronosticoInsumo[] {
  return insumos.filter((i) => i.prioridad !== null);
}
