/** RestoFlow AI · avisos del momento para el rol de quien está conectado (GET /api/avisos). */

export type NivelAviso = 'URGENTE' | 'ATENCION' | 'INFO';

export type TipoAviso =
  | 'PLATO_DEMORADO' | 'PEDIDO_LISTO' | 'SIN_COBRAR' | 'INSUMO_EN_MINIMO' | 'INSUMO_SE_ACABA' | 'DEMANDA';

export interface Aviso {
  clave: string;
  tipo: TipoAviso;
  nivel: NivelAviso;
  titulo: string;
  mensaje: string;
  enlace: string | null;
  desde: string | null;
}

export interface AvisosDelMomento {
  generadoEn: string;
  avisos: Aviso[];
}
