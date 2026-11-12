// Pruebas con Jest · interceptor HTTP: agrega el token solo a nuestro API y cierra la sesión si el token vence.
import { HttpHandler, HttpRequest } from '@angular/common/http';
import { Injector, runInInjectionContext } from '@angular/core';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { Rol } from '../entities';
import { environment } from '../../../environments/environment';
import { sesionFalsa } from '../../../testing/angular';

function preparar(rol: Rol | null, respuesta = of({})) {
  const auth = sesionFalsa(rol);
  const router = { navigate: jest.fn() };
  const injector = Injector.create({ providers: [
    { provide: AuthService, useValue: auth }, { provide: Router, useValue: router }, AuthInterceptor,
  ] });
  const interceptor = runInInjectionContext(injector, () => new AuthInterceptor());
  const siguiente = { handle: jest.fn(() => respuesta) };
  return { interceptor, siguiente, auth, router, handler: siguiente as unknown as HttpHandler };
}

const enviada = (s: { handle: jest.Mock }) => s.handle.mock.calls[0][0] as HttpRequest<unknown>;

describe('Interceptor de autenticación', () => {
  it('agrega "Authorization: Bearer" a las peticiones a nuestro API', () => {
    const { interceptor, siguiente, handler } = preparar('ADMIN');
    interceptor.intercept(new HttpRequest('GET', `${environment.apiUrl}/pedidos`), handler).subscribe();
    expect(enviada(siguiente).headers.get('Authorization')).toBe('Bearer token-de-prueba');
  });

  it('no envía el token a otras direcciones ni cuando no hay sesión', () => {
    const externo = preparar('ADMIN');
    externo.interceptor.intercept(new HttpRequest('GET', 'https://images.openfoodfacts.org/x.jpg'), externo.handler).subscribe();
    expect(enviada(externo.siguiente).headers.has('Authorization')).toBe(false);
    const sinSesion = preparar(null);
    sinSesion.interceptor.intercept(new HttpRequest('GET', `${environment.apiUrl}/auth/login`), sinSesion.handler).subscribe();
    expect(enviada(sinSesion.siguiente).headers.has('Authorization')).toBe(false);
  });

  it('un 401 con sesión cierra la sesión y lleva al login; otros errores solo se reenvían', () => {
    const vencido = preparar('MESERO', throwError(() => ({ status: 401 })));
    const errores: unknown[] = [];
    vencido.interceptor.intercept(new HttpRequest('GET', `${environment.apiUrl}/pedidos`), vencido.handler)
      .subscribe({ error: (e) => errores.push(e) });
    expect(vencido.auth.logout).toHaveBeenCalled();
    expect(vencido.router.navigate).toHaveBeenCalledWith(['/login']);
    expect(errores).toHaveLength(1);

    const otro = preparar('MESERO', throwError(() => ({ status: 500 })));
    otro.interceptor.intercept(new HttpRequest('GET', `${environment.apiUrl}/pedidos`), otro.handler).subscribe({ error: () => undefined });
    expect(otro.auth.logout).not.toHaveBeenCalled();
  });
});
