// RF-02 · Pedido que el mesero está armando (antes de enviarlo a cocina).
// Funciones puras que devuelven un carrito NUEVO (no modifican el anterior), como lo necesitan las signals.
// Se prueban con Jest (carrito.test.ts).
import type { EstadoCocina, Pedido } from '../entities/pedido';
import type { Producto } from '../entities/producto';
import type { FiltroPedidos, PedidoRequest } from '../models/pedido.models';

export interface ItemCarrito {
  producto: Producto;
  cantidad: number;
  nota: string;
}

/** Cantidad máxima de un mismo producto en una línea. */
export const MAXIMO_POR_PRODUCTO = 99;

export type FiltroEstado = 'ACTIVOS' | EstadoCocina | 'PAGADO' | 'CANCELADO' | 'TODOS';

/** Si el producto ya está, suma 1; si no, lo agrega con cantidad 1. */
export function agregarProducto(items: ItemCarrito[], producto: Producto): ItemCarrito[] {
  const existente = items.find((i) => i.producto.id === producto.id);
  return existente ? cambiarCantidad(items, existente, 1) : [...items, { producto, cantidad: 1, nota: '' }];
}

/** Suma o resta; si llega a 0 se quita la línea; nunca pasa de 99. */
export function cambiarCantidad(items: ItemCarrito[], item: ItemCarrito, cambio: number): ItemCarrito[] {
  const nueva = item.cantidad + cambio;
  if (nueva < 1) return quitarItem(items, item);
  return items.map((i) => (i === item ? { ...i, cantidad: Math.min(nueva, MAXIMO_POR_PRODUCTO) } : i));
}

export function cambiarNota(items: ItemCarrito[], item: ItemCarrito, nota: string): ItemCarrito[] {
  return items.map((i) => (i === item ? { ...i, nota } : i));
}

export function quitarItem(items: ItemCarrito[], item: ItemCarrito): ItemCarrito[] {
  return items.filter((i) => i !== item);
}

export function totalCarrito(items: ItemCarrito[]): number {
  return items.reduce((suma, item) => suma + item.producto.precio * item.cantidad, 0);
}

/** Datos que se envían al API; los textos vacíos viajan como null. */
export function aPedidoRequest(mesa: number | string | null, observaciones: string | null | undefined,
                               items: ItemCarrito[]): PedidoRequest {
  return {
    numeroMesa: Number(mesa),
    observaciones: observaciones?.trim() || null,
    lineas: items.map((i) => ({ productoId: i.producto.id, cantidad: i.cantidad, nota: i.nota.trim() || null })),
  };
}

/**
 * Para editar un pedido: vuelve a armar el carrito con los productos del menú actual.
 * Si alguno ya no está disponible devuelve su nombre en "faltante" (no se puede editar).
 */
export function carritoDesdePedido(pedido: Pick<Pedido, 'lineas'>, productos: Producto[]):
  { items: ItemCarrito[]; faltante: string | null } {
  const items: ItemCarrito[] = [];
  for (const linea of pedido.lineas) {
    const producto = productos.find((p) => p.id === linea.productoId);
    if (!producto) return { items: [], faltante: linea.productoNombre };
    items.push({ producto, cantidad: linea.cantidad, nota: linea.nota ?? '' });
  }
  return { items, faltante: null };
}

/** Menú visible: por categoría ("Todas" = sin filtro) y por texto, sin importar mayúsculas. */
export function filtrarProductos(productos: Producto[], categoria: string, texto: string): Producto[] {
  const buscado = texto.trim().toLowerCase();
  return productos.filter((p) =>
    (categoria === 'Todas' || p.categoriaNombre === categoria)
    && (!buscado || p.nombre.toLowerCase().includes(buscado)));
}

/** "Todas" y luego cada categoría una sola vez, en el orden en que aparecen. */
export function categoriasDe(productos: Producto[]): string[] {
  return ['Todas', ...new Set(productos.map((p) => p.categoriaNombre))];
}

/** Traduce el filtro de la pantalla a los parámetros del API. */
export function filtroDePedidos(estado: FiltroEstado, mesa: number | null): FiltroPedidos {
  const filtro: FiltroPedidos = { mesa };
  if (estado === 'ACTIVOS') filtro.estadoPedido = 'ABIERTO';
  else if (estado === 'PAGADO' || estado === 'CANCELADO') filtro.estadoPedido = estado;
  else if (estado !== 'TODOS') filtro.estadoCocina = estado;
  return filtro;
}
