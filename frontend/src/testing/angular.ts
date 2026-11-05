// Ayudas para probar pantallas y servicios de Angular con Jest, sin navegador y sin backend.
// Cada pantalla se crea como una clase dentro de un inyector de Angular con servicios falsos:
// así se prueba su lógica real (signals, formularios, llamadas al API, mensajes) en milisegundos.
import { Injector, Provider, runInInjectionContext } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../app/core/services/auth.service';
import { Rol, Usuario } from '../app/core/entities';

/** Una pantalla creada para la prueba y cómo destruirla (detiene sus temporizadores y suscripciones). */
export interface Montaje<T> {
  componente: T;
  destruir: () => void;
}

/** Crea la clase dentro de un inyector con los proveedores falsos indicados (como lo haría Angular). */
export function crear<T>(Tipo: new () => T, proveedores: Provider[] = []): Montaje<T> {
  const injector = Injector.create({ providers: [FormBuilder, ...proveedores] });
  const componente = runInInjectionContext(injector, () => new Tipo());
  return { componente, destruir: () => (injector as unknown as { destroy(): void }).destroy() };
}

/** Respuesta correcta del API. */
export function ok<T>(valor: T): Observable<T> {
  return of(valor);
}

/** Respuesta con error del API, con la forma de HttpErrorResponse que usan las pantallas. */
export function falla(status: number, mensaje?: string): Observable<never> {
  return throwError(() => ({ status, error: mensaje ? { mensaje } : null }));
}

/** Sesión falsa: el usuario con ese rol (o sin sesión si rol es null). */
export function sesionFalsa(rol: Rol | null, nombre = 'Laura Gómez') {
  const usuario: Usuario | null = rol ? { id: 7, nombre, email: 'laura@restoflow.com', rol, activo: true } : null;
  return {
    usuario,
    token: rol ? 'token-de-prueba' : null,
    tieneRol: (...roles: Rol[]) => rol !== null && roles.includes(rol),
    estaAutenticado: () => rol !== null,
    logout: jest.fn(),
    login: jest.fn(),
    recuperar: jest.fn(),
    restablecer: jest.fn(),
  };
}

/** Proveedor de AuthService con la sesión falsa. */
export function conSesion(rol: Rol | null, nombre?: string): Provider {
  return { provide: AuthService, useValue: sesionFalsa(rol, nombre) };
}

/** Funciones del navegador que usan las pantallas (scroll, imprimir, confirmar, preguntar). */
export function simularNavegador(respuestas: { confirmar?: boolean; preguntar?: string | null } = {}) {
  const g = globalThis as Record<string, unknown>;
  const ventana = { scrollTo: jest.fn(), print: jest.fn() };
  const confirmar = jest.fn(() => respuestas.confirmar ?? true);
  const preguntar = jest.fn(() => (respuestas.preguntar === undefined ? null : respuestas.preguntar));
  g['window'] = ventana;
  g['confirm'] = confirmar;
  g['prompt'] = preguntar;
  return {
    ventana, confirmar, preguntar,
    restaurar: () => {
      delete g['window'];
      delete g['confirm'];
      delete g['prompt'];
    },
  };
}
