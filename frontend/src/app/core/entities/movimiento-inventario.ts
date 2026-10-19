import type { UnidadMedida } from './ingrediente';

export type TipoMovimiento = 'ENTRADA' | 'SALIDA' | 'AJUSTE';

/** Entidad MovimientoInventario (tabla movimientos_inventario): historial del inventario (RF-12). */
export interface MovimientoInventario {
  id: number;
  ingredienteId: number;
  ingredienteNombre: string;
  unidad: UnidadMedida;
  tipo: TipoMovimiento;
  /** En un AJUSTE es el valor contado (el stock quedó en esa cantidad). */
  cantidad: number;
  motivo: string | null;
  usuarioId: number | null;
  /** "Sistema" cuando lo generó una venta. */
  usuarioNombre: string;
  /** Pedido que originó la salida (descuento automático por venta). */
  pedidoId: number | null;
  fecha: string;
}
