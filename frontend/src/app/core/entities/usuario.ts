/** Rol del personal. Coincide con el enum Rol del backend. */
export type Rol = 'ADMIN' | 'MESERO' | 'COCINA' | 'INVENTARIO';

/** Entidad Usuario (tabla usuarios). El API nunca envía la contraseña. */
export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rol: Rol;
  activo: boolean;
}

export const NOMBRES_ROL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  MESERO: 'Mesero',
  COCINA: 'Cocina',
  INVENTARIO: 'Inventario',
};
