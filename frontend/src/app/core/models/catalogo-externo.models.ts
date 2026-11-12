// GET /api/inventario/catalogo-externo/{codigo} · API externa Open Food Facts (la consulta el backend).
import type { UnidadMedida } from '../entities';

export interface ProductoExterno {
  codigoBarras: string;
  nombre: string | null;
  marca: string | null;
  /** Como viene en el empaque: "1 kg", "500 g", "1 L". */
  cantidad: string | null;
  imagenUrl: string | null;
  fuente: string;
  /** Nombre listo para el insumo, por ejemplo "Arroz blanco · Diana". */
  nombreSugerido: string;
  unidadSugerida: UnidadMedida;
}
