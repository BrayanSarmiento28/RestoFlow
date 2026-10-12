export type TipoMovimiento = 'ENTRADA' | 'SALIDA' | 'AJUSTE';

/** Entidad MovimientoInventario (tabla movimientos_inventario): historial del inventario (RF-12). */
export interface MovimientoInventario {
  id: number;
  ingredienteId: number;
  tipo: TipoMovimiento;
  cantidad: number;
  motivo: string | null;
  usuarioId: number | null;
  pedidoId: number | null;
  fecha: string;
}
