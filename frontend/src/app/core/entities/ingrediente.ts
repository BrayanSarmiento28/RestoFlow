/** Unidad en la que se mide un ingrediente. */
export type UnidadMedida = 'GRAMO' | 'KILOGRAMO' | 'MILILITRO' | 'LITRO' | 'UNIDAD';

/** Abreviatura para mostrar junto a las cantidades: 150 g, 2 und. */
export const ABREVIATURA_UNIDAD: Record<UnidadMedida, string> = {
  GRAMO: 'g',
  KILOGRAMO: 'kg',
  MILILITRO: 'ml',
  LITRO: 'l',
  UNIDAD: 'und',
};

export const NOMBRES_UNIDAD: Record<UnidadMedida, string> = {
  GRAMO: 'Gramos',
  KILOGRAMO: 'Kilogramos',
  MILILITRO: 'Mililitros',
  LITRO: 'Litros',
  UNIDAD: 'Unidades',
};

/** Entidad Ingrediente (tabla ingredientes): insumo del inventario (RF-05). */
export interface Ingrediente {
  id: number;
  nombre: string;
  unidad: UnidadMedida;
  stockActual: number;
  stockMinimo: number;
  activo: boolean;
  /** Calculados por el backend: stock ≤ mínimo, y stock = 0. */
  enMinimo: boolean;
  agotado: boolean;
}

/** Entidad RecetaIngrediente (tabla receta_ingredientes): cuánto lleva un producto de un ingrediente (RF-06). */
export interface RecetaIngrediente {
  ingredienteId: number;
  ingredienteNombre: string;
  unidad: UnidadMedida;
  /** Cantidad por cada unidad vendida del producto. */
  cantidad: number;
}
