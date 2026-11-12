export type PrioridadAlerta = 'ALTA' | 'MEDIA' | 'BAJA';

/** Entidad Alerta (tabla alertas): bajo inventario (RF-07) y, más adelante, recomendaciones de RestoFlow AI. */
export interface Alerta {
  id: number;
  ingredienteId: number | null;
  ingredienteNombre: string | null;
  mensaje: string;
  prioridad: PrioridadAlerta;
  atendida: boolean;
  fechaCreacion: string;
  fechaAtencion: string | null;
}
