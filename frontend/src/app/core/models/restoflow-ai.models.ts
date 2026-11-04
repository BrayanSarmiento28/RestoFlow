// GET /api/restoflow-ai/analisis y POST /api/restoflow-ai/alertas · RestoFlow AI (Sprint 5, RF-09 a RF-11).
// La misma forma que los records del backend (domain/ia y ConsultarRestoFlowAIUseCase).
import type { PrioridadAlerta } from '../entities/alerta';
import type { UnidadMedida } from '../entities/ingrediente';

/** Día de la semana tal como lo envía Java (DayOfWeek). */
export type DiaSemana = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export type TipoRecomendacion = 'COMPRA' | 'TENDENCIA' | 'DEMANDA' | 'PRODUCTO' | 'INFORMACION';

/** Unidades vendidas de un producto esta semana y la anterior. */
export interface ProductoTendencia {
  productoId: number;
  nombre: string;
  unidadesSemana: number;
  unidadesSemanaAnterior: number;
  tendenciaPorcentaje: number | null;
}

/** Promedio vendido un día de la semana. */
export interface VentaDia {
  dia: DiaSemana;
  promedio: number;
}

export interface DemandaProducto {
  productoId: number;
  nombre: string;
  unidades: number;
}

/** RES-41 · Demanda esperada para mañana. */
export interface Prediccion {
  fecha: string;
  ventasEsperadas: number;
  pedidosEsperados: number;
  basadoEnDias: number;
  variacionPromedio: number | null;
  productos: DemandaProducto[];
}

/** RF-09 · Ventas de las últimas 4 semanas (días completos). */
export interface AnalisisVentas {
  diasConVentas: number;
  datosSuficientes: boolean;
  ventasSemana: number;
  tendenciaVentas: number | null;
  diaMasFuerte: DiaSemana | null;
  horaPico: number | null;
  productos: ProductoTendencia[];
  ventasPorDia: VentaDia[];
  prediccion: Prediccion | null;
}

/** RF-10 · Consumo, cobertura y compra sugerida de un insumo. */
export interface PronosticoInsumo {
  ingredienteId: number;
  nombre: string;
  unidad: UnidadMedida;
  stockActual: number;
  stockMinimo: number;
  consumoDiario: number;
  consumoSemana: number;
  tendenciaPorcentaje: number | null;
  diasCobertura: number | null;
  prioridad: PrioridadAlerta | null;
  cantidadSugerida: number;
}

/** RF-11 · Recomendación lista para mostrar. */
export interface Recomendacion {
  tipo: TipoRecomendacion;
  prioridad: PrioridadAlerta;
  titulo: string;
  detalle: string;
  ingredienteId: number | null;
  productoId: number | null;
}

export interface AnalisisRestoFlowAI {
  generadoEn: string;
  ventas: AnalisisVentas;
  insumos: PronosticoInsumo[];
  recomendaciones: Recomendacion[];
}

/** RES-40 · Resultado de revisar las alertas anticipadas. */
export interface ResultadoAlertas {
  creadas: number;
  cerradas: number;
  /** Insumos que se acaban pronto pero ya tenían una alerta pendiente (no se repite el aviso). */
  yaAvisados: number;
}
