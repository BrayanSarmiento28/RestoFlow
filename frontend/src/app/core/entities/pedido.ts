/** RF-03 · Estados de cocina. Avanzan en este orden, sin saltos. */
export type EstadoCocina = 'PENDIENTE' | 'EN_PREPARACION' | 'LISTO' | 'ENTREGADO';

/** Estado comercial del pedido. */
export type EstadoPedido = 'ABIERTO' | 'PAGADO' | 'CANCELADO';

export const NOMBRES_ESTADO_COCINA: Record<EstadoCocina, string> = {
  PENDIENTE: 'Pendiente',
  EN_PREPARACION: 'En preparación',
  LISTO: 'Listo',
  ENTREGADO: 'Entregado',
};

export const NOMBRES_ESTADO_PEDIDO: Record<EstadoPedido, string> = {
  ABIERTO: 'Abierto',
  PAGADO: 'Pagado',
  CANCELADO: 'Cancelado',
};

/** El único estado al que puede pasar un pedido desde el actual (null si ya terminó). */
export function siguienteEstado(estado: EstadoCocina): EstadoCocina | null {
  const orden: EstadoCocina[] = ['PENDIENTE', 'EN_PREPARACION', 'LISTO', 'ENTREGADO'];
  const i = orden.indexOf(estado);
  return i >= 0 && i < orden.length - 1 ? orden[i + 1] : null;
}

/** Entidad DetallePedido (tabla detalle_pedidos): una línea del pedido. */
export interface DetallePedido {
  id: number;
  productoId: number;
  productoNombre: string;
  cantidad: number;
  /** Precio del producto en el momento del pedido. */
  precioUnitario: number;
  subtotal: number;
  nota: string | null;
}

/** Entidad Pedido (tabla pedidos). */
export interface Pedido {
  id: number;
  numeroMesa: number;
  meseroId: number;
  meseroNombre: string;
  lineas: DetallePedido[];
  observaciones: string | null;
  estadoCocina: EstadoCocina;
  estadoPedido: EstadoPedido;
  total: number;
  fechaCreacion: string;
  fechaInicioPreparacion: string | null;
  fechaListo: string | null;
  fechaEntrega: string | null;
  fechaCierre: string | null;
}
