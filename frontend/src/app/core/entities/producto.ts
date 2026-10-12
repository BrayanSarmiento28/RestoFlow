/** Entidad Producto (tabla productos): plato o bebida que se vende. */
export interface Producto {
  id: number;
  nombre: string;
  descripcion: string | null;
  precio: number;
  categoriaId: number;
  categoriaNombre: string;
  /** false = no aparece para la venta (nunca se borra). */
  activo: boolean;
}
