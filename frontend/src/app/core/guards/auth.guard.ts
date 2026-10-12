import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Rol } from '../entities';

/** Solo deja pasar si hay una sesión vigente. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estaAutenticado() ? true : inject(Router).createUrlTree(['/login']);
};

/** Solo deja pasar al rol ADMIN (HU-03). */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.tieneRol('ADMIN') ? true : inject(Router).createUrlTree(['/inicio']);
};

/**
 * Deja pasar solo a los roles indicados en la ruta, por ejemplo:
 * { path: 'cocina', canActivate: [rolGuard], data: { roles: ['COCINA', 'ADMIN'] } }
 */
export const rolGuard: CanActivateFn = (ruta) => {
  const roles = (ruta.data['roles'] ?? []) as Rol[];
  return inject(AuthService).tieneRol(...roles) ? true : inject(Router).createUrlTree(['/inicio']);
};
