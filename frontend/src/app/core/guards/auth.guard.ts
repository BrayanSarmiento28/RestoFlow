import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

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
