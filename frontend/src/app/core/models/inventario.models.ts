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
}
