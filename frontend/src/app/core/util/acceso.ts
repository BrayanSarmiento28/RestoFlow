// RF-01 · Decisiones de acceso que usan los guards de las rutas y el interceptor HTTP.
// Funciones puras (sin Angular): se prueban con Jest (acceso.test.ts).
import type { Rol } from '../entities/usuario';

/** true = puede entrar; si no, la ruta a la que se le envía. */
export type DecisionAcceso = true | '/login' | '/inicio';

/**
 * Sin sesión → al login. Con sesión pero sin el rol pedido → al inicio. Si la ruta no pide roles, basta la sesión.
 */
export function decidirAcceso(autenticado: boolean, rolUsuario: Rol | null | undefined,
                              rolesPermitidos: readonly Rol[] = []): DecisionAcceso {
  if (!autenticado) return '/login';
  if (rolesPermitidos.length === 0) return true;
  return rolUsuario && rolesPermitidos.includes(rolUsuario) ? true : '/inicio';
}

/** El token solo se envía a nuestro API, nunca a otra dirección (por ejemplo, la foto de un producto externo). */
export function debeAdjuntarToken(url: string, apiUrl: string, token: string | null): boolean {
  return !!token && url.startsWith(apiUrl);
}

/** Un 401 con sesión abierta significa que el token venció o fue rechazado: hay que volver a iniciar sesión. */
export function debeCerrarSesion(estadoHttp: number, teniaToken: boolean): boolean {
  return estadoHttp === 401 && teniaToken;
}
