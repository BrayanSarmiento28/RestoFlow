// Pruebas con Jest · guards de las rutas: sin sesión al login, sin el rol al inicio.
import { Injector, runInInjectionContext } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { adminGuard, authGuard, rolGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { Rol } from '../entities';
import { sesionFalsa } from '../../../testing/angular';

function probar(guard: typeof authGuard, rol: Rol | null, data: Record<string, unknown> = {}) {
  const router = { createUrlTree: jest.fn((partes: string[]) => `redirige a ${partes[0]}`) };
  const injector = Injector.create({ providers: [
    { provide: AuthService, useValue: sesionFalsa(rol) },
    { provide: Router, useValue: router },
  ] });
  const ruta = { data } as unknown as ActivatedRouteSnapshot;
  return runInInjectionContext(injector, () => guard(ruta, {} as RouterStateSnapshot));
}

describe('Guards de las rutas', () => {
  it('authGuard: con sesión deja pasar; sin sesión envía al login', () => {
    expect(probar(authGuard, 'MESERO')).toBe(true);
    expect(probar(authGuard, null)).toBe('redirige a /login');
  });

  it('adminGuard: solo el administrador', () => {
    expect(probar(adminGuard, 'ADMIN')).toBe(true);
    expect(probar(adminGuard, 'COCINA')).toBe('redirige a /inicio');
  });

  it('rolGuard: usa los roles de la ruta; sin roles basta la sesión', () => {
    expect(probar(rolGuard, 'COCINA', { roles: ['COCINA', 'ADMIN'] })).toBe(true);
    expect(probar(rolGuard, 'MESERO', { roles: ['COCINA', 'ADMIN'] })).toBe('redirige a /inicio');
    expect(probar(rolGuard, 'MESERO')).toBe(true);
  });
});
