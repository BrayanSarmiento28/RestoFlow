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
