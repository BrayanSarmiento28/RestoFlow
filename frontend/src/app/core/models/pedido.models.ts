// Datos que se envían al API de menú, pedidos y cocina (docs/sprint-2.md).
import type { EstadoCocina } from '../entities';

export interface LineaPedidoRequest {
  productoId: number;
  cantidad: number;
  nota?: string | null;
}

export interface PedidoRequest {
  numeroMesa: number;
  lineas: LineaPedidoRequest[];
  observaciones?: string | null;
}

export interface CambioEstadoRequest {
  estado: EstadoCocina;
}

export interface ProductoRequest {
  nombre: string;
  descripcion: string | null;
  precio: number;
  categoriaId: number;
}

export interface FiltroPedidos {
  mesa?: number | null;
  estadoCocina?: EstadoCocina | null;
  estadoPedido?: 'ABIERTO' | 'PAGADO' | 'CANCELADO' | null;
}

/** Mesa con un pedido abierto (GET /api/pedidos/mesas-ocupadas). No recibe pedidos nuevos hasta liberarse. */
export interface MesaOcupada {
  numeroMesa: number;
  pedidoId: number;
  meseroNombre: string;
  estadoCocina: EstadoCocina;
}
