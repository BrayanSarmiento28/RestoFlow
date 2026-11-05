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
  /** Se agregó con el pedido ya en cocina (el cliente pidió algo más). */
  adicion?: boolean;
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
  /** Número del pedido en su día (#1, #2...): vuelve a empezar cada día. Null en pedidos antiguos. */
  numeroDelDia?: number | null;
  /** Cómo se pagó (solo en pedidos Pagados). */
  metodoPago?: MetodoPago | null;
  /** Hora de la última adición. */
  fechaAdicion?: string | null;
}

/** Métodos de pago al registrar la venta (por ahora efectivo y transferencia). */
export type MetodoPago = 'EFECTIVO' | 'TRANSFERENCIA';

export const NOMBRES_METODO_PAGO: Record<MetodoPago, string> = {
  EFECTIVO: 'Efectivo',
  TRANSFERENCIA: 'Transferencia',
};

/** El número que ve el administrador: el del día; en pedidos antiguos (sin número del día), el id. */
export function numeroPedido(p: Pick<Pedido, 'id' | 'numeroDelDia'>): number {
  return p.numeroDelDia ?? p.id;
}
