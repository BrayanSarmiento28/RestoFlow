// Tipos que coinciden con el contrato del API acordado con el backend (docs/sprint-1.md)

export type Rol = 'ADMIN' | 'MESERO' | 'COCINA' | 'INVENTARIO';

export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rol: Rol;
  activo: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiraEnSegundos: number;
  usuario: Usuario;
}

export interface MensajeResponse {
  mensaje: string;
}

export interface ErrorResponse {
  status: number;
  error: string;
  mensaje: string;
  campos?: Record<string, string>;
}

export interface CrearUsuarioRequest {
  nombre: string;
  email: string;
  password: string;
  rol: Rol;
}
