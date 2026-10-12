export type PrioridadAlerta = 'ALTA' | 'MEDIA' | 'BAJA';

/** Entidad Alerta (tabla alertas): bajo inventario y recomendaciones de RestoFlow AI (RF-07, RF-11). */
export interface Alerta {
  id: number;
  ingredienteId: number | null;
  mensaje: string;
  prioridad: PrioridadAlerta;
  atendida: boolean;
  fechaCreacion: string;
  fechaAtencion: string | null;
}
