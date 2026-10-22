import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Rol } from '../entities';
import { decidirAcceso } from '../util/acceso';

/** Aplica la decisión de core/util/acceso.ts: deja pasar o redirige al login o al inicio. */
function aplicar(roles: Rol[] = []) {
  const auth = inject(AuthService);
  const decision = decidirAcceso(auth.estaAutenticado(), auth.usuario?.rol, roles);
  return decision === true ? true : inject(Router).createUrlTree([decision]);
}

/** Solo deja pasar si hay una sesión vigente. */
export const authGuard: CanActivateFn = () => aplicar();

/** Solo deja pasar al rol ADMIN (HU-03). */
export const adminGuard: CanActivateFn = () => aplicar(['ADMIN']);

/**
 * Deja pasar solo a los roles indicados en la ruta, por ejemplo:
 * { path: 'cocina', canActivate: [rolGuard], data: { roles: ['COCINA', 'ADMIN'] } }
 */
export const rolGuard: CanActivateFn = (ruta) => aplicar((ruta.data['roles'] ?? []) as Rol[]);
