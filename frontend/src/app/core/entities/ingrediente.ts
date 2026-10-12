/** Unidad en la que se mide un ingrediente. */
export type UnidadMedida = 'GRAMO' | 'KILOGRAMO' | 'MILILITRO' | 'LITRO' | 'UNIDAD';

/** Entidad Ingrediente (tabla ingredientes). Se usa desde el sprint de inventario (RF-05). */
export interface Ingrediente {
  id: number;
  nombre: string;
  unidad: UnidadMedida;
  stockActual: number;
  stockMinimo: number;
  activo: boolean;
}

/** Entidad RecetaIngrediente (tabla receta_ingredientes): cuánto lleva un producto de un ingrediente. */
export interface RecetaIngrediente {
  id: number;
  productoId: number;
  ingredienteId: number;
  cantidad: number;
}
