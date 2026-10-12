// Datos que se envían y reciben en el login y la gestión de cuentas (docs/sprint-1.md).
// Las entidades (Usuario, Rol, Pedido...) viven en core/entities.
import type { Rol, Usuario } from '../entities';

export type { Rol, Usuario };

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
