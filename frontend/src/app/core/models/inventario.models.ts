// Datos que se envían y reciben del API de inventario, panel e historial (docs/sprint-3.md).
import type { Pedido, UnidadMedida } from '../entities';

export interface IngredienteRequest {
  nombre: string;
  unidad: UnidadMedida;
  stockMinimo: number;
  /** Solo al crear: queda registrado como una entrada. */
  stockInicial?: number | null;
}

export interface MovimientoRequest {
  cantidad: number;
  motivo?: string | null;
}

export interface AjusteRequest {
  stockContado: number;
  motivo?: string | null;
}

export interface LineaRecetaRequest {
  ingredienteId: number;
  cantidad: number;
}

/** RF-08 · GET /api/panel/resumen */
export interface ResumenPanel {
  dia: string;
  ventas: number;
  pedidosPagados: number;
  ticketPromedio: number;
  ventasDiaAnterior: number;
  pedidosAbiertos: number;
  pedidosEnCocina: number;
  pedidosCancelados: number;
  ventasPorHora: { hora: number; total: number; pedidos: number }[];
  masVendidos: { productoId: number; nombre: string; cantidad: number; total: number }[];
  insumosEnMinimo: { id: number; nombre: string; stockActual: number; stockMinimo: number; unidad: UnidadMedida }[];
  alertasPendientes: number;
  /** Cierre del día: pedidos registrados hoy (cobrados, abiertos y cancelados). */
  pedidosRegistrados: number;
  /** Cierre del día: pedidos cobrados y total vendido por cada mesero. */
  porMesero: { meseroId: number; meseroNombre: string; pedidos: number; total: number }[];
  /** Cierre del día: lo que entró en efectivo y por transferencia (para cuadrar la caja). */
  porMetodoPago: { metodo: 'EFECTIVO' | 'TRANSFERENCIA'; pedidos: number; total: number }[];
}

/** RF-12 · GET /api/historial/ventas */
export interface ResumenVentas {
  desde: string;
  hasta: string;
  total: number;
  cantidad: number;
  ticketPromedio: number;
  porDia: { dia: string; total: number; cantidad: number }[];
  ventas: Pedido[];
  /** RF-09 · Tiempo de preparación de cada plato vendido en el periodo (del más lento al más rápido). */
  tiemposPreparacion: TiempoPlato[];
}

/** RF-09 · Minutos desde que se registra el pedido hasta que la cocina lo marca Listo. */
export interface TiempoPlato {
  productoId: number;
  nombre: string;
  /** Pedidos que se pudieron medir (los que tienen adición no cuentan). */
  pedidos: number;
  /** Tiempo normal: la mediana, que no se dispara por un pedido olvidado. */
  medianaMinutos: number;
  promedioMinutos: number;
}

/** Usabilidad · GET /api/cocina/stock: insumo de un plato de las comandas que está en su mínimo o agotado. */
export interface StockBajo {
  productoId: number;
  ingredienteId: number;
  insumo: string;
  stockActual: number;
  stockMinimo: number;
  unidad: UnidadMedida;
  agotado: boolean;
}
