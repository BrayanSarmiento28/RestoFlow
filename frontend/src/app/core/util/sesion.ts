// RF-01 · Sesión del usuario en el navegador (token JWT, datos del usuario y vencimiento).
// Clase sin Angular: recibe los almacenes (localStorage / sessionStorage) y el reloj, así Jest la prueba
// con almacenes falsos en memoria (sesion.test.ts). AuthService la usa con los almacenes reales.
import type { Rol, Usuario } from '../entities/usuario';

/** Lo mínimo que se necesita de localStorage o sessionStorage. */
export interface AlmacenClaveValor {
  getItem(clave: string): string | null;
  setItem(clave: string, valor: string): void;
  removeItem(clave: string): void;
}

export const CLAVES_SESION = { token: 'rf_token', usuario: 'rf_usuario', expira: 'rf_expira' } as const;

export class AlmacenSesion {
  /**
   * @param persistente sobrevive al cerrar el navegador (localStorage, con "Recordarme")
   * @param temporal    se borra al cerrar la pestaña (sessionStorage)
   * @param ahora       reloj en milisegundos (en las pruebas se usa uno fijo)
   */
  constructor(
    private readonly persistente: AlmacenClaveValor,
    private readonly temporal: AlmacenClaveValor,
    private readonly ahora: () => number = () => Date.now(),
  ) {}

  /** Guarda la sesión del login. Antes borra cualquier sesión anterior. */
  guardar(token: string, usuario: Usuario, expiraEnSegundos: number, recordarme: boolean): void {
    this.cerrar();
    const almacen = recordarme ? this.persistente : this.temporal;
    almacen.setItem(CLAVES_SESION.token, token);
    almacen.setItem(CLAVES_SESION.usuario, JSON.stringify(usuario));
    almacen.setItem(CLAVES_SESION.expira, String(this.ahora() + expiraEnSegundos * 1000));
  }

  cerrar(): void {
    for (const almacen of [this.persistente, this.temporal]) {
      Object.values(CLAVES_SESION).forEach((clave) => almacen.removeItem(clave));
    }
  }

  get token(): string | null {
    return this.leer(CLAVES_SESION.token);
  }

  /** Datos del usuario; si el texto guardado está dañado, se toma como sin sesión. */
  get usuario(): Usuario | null {
    const json = this.leer(CLAVES_SESION.usuario);
    if (!json) return null;
    try {
      return JSON.parse(json) as Usuario;
    } catch {
      return null;
    }
  }

  /** Hay token y no ha vencido. Si venció, se borra la sesión. */
  vigente(): boolean {
    const expira = Number(this.leer(CLAVES_SESION.expira) ?? 0);
    if (!this.token || this.ahora() > expira) {
      this.cerrar();
      return false;
    }
    return true;
  }

  tieneRol(...roles: Rol[]): boolean {
    const usuario = this.usuario;
    return !!usuario && roles.includes(usuario.rol);
  }

  private leer(clave: string): string | null {
    return this.persistente.getItem(clave) ?? this.temporal.getItem(clave);
  }
}
