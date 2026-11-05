// Pruebas con Jest · AuthService: login, recuperación de contraseña y sesión guardada en el navegador.
import { HttpClient } from '@angular/common/http';
import { Injector, runInInjectionContext } from '@angular/core';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

/** localStorage / sessionStorage en memoria. */
function almacen() {
  const datos = new Map<string, string>();
  return {
    getItem: (k: string) => datos.get(k) ?? null,
    setItem: (k: string, v: string) => void datos.set(k, v),
    removeItem: (k: string) => void datos.delete(k),
    datos,
  };
}

describe('AuthService', () => {
  const g = globalThis as Record<string, unknown>;
  let local: ReturnType<typeof almacen>;
  let sesion: ReturnType<typeof almacen>;
  let http: { post: jest.Mock; patch: jest.Mock };
  let auth: AuthService;

  beforeEach(() => {
    local = almacen();
    sesion = almacen();
    g['localStorage'] = local;
    g['sessionStorage'] = sesion;
    const usuario = { id: 1, nombre: 'Admin', email: 'admin@restoflow.com', rol: 'ADMIN', activo: true };
    http = {
      post: jest.fn(() => of({ token: 'abc', expiraEnSegundos: 3600, usuario, mensaje: 'Listo' })),
      patch: jest.fn(() => of({ mensaje: 'Listo' })),
    };
    const injector = Injector.create({ providers: [{ provide: HttpClient, useValue: http }] });
    auth = runInInjectionContext(injector, () => new AuthService());
  });

  afterEach(() => {
    delete g['localStorage'];
    delete g['sessionStorage'];
  });

  it('sin login no hay sesión', () => {
    expect(auth.estaAutenticado()).toBe(false);
    expect(auth.token).toBeNull();
    expect(auth.usuario).toBeNull();
    expect(auth.tieneRol('ADMIN')).toBe(false);
  });

  it('el login guarda la sesión (con "Recordarme" en el almacén persistente) y logout la borra', () => {
    auth.login({ email: 'admin@restoflow.com', password: 'x' }, true).subscribe();
    expect(http.post).toHaveBeenCalledWith(`${environment.apiUrl}/auth/login`, { email: 'admin@restoflow.com', password: 'x' });
    expect(local.datos.size).toBe(3);
    expect(auth.token).toBe('abc');
    expect(auth.usuario?.nombre).toBe('Admin');
    expect(auth.estaAutenticado()).toBe(true);
    expect(auth.tieneRol('ADMIN', 'COCINA')).toBe(true);
    auth.logout();
    expect(local.datos.size).toBe(0);
    expect(auth.estaAutenticado()).toBe(false);
  });

  it('sin "Recordarme" la sesión va al almacén temporal', () => {
    auth.login({ email: 'admin@restoflow.com', password: 'x' }, false).subscribe();
    expect(sesion.datos.size).toBe(3);
    expect(local.datos.size).toBe(0);
  });

  it('recuperar, restablecer y cambiar la contraseña llaman al API con los datos del formulario', () => {
    auth.recuperar('a@b.com').subscribe();
    expect(http.post).toHaveBeenLastCalledWith(`${environment.apiUrl}/auth/recuperar`, { email: 'a@b.com' });
    auth.restablecer('a@b.com', '123456', 'Nueva2026').subscribe();
    expect(http.post).toHaveBeenLastCalledWith(`${environment.apiUrl}/auth/restablecer`,
      { email: 'a@b.com', codigo: '123456', nuevaPassword: 'Nueva2026' });
    auth.cambiarContrasena('Vieja2026', 'Nueva2027').subscribe();
    expect(http.patch).toHaveBeenCalledWith(`${environment.apiUrl}/auth/contrasena`, { actual: 'Vieja2026', nueva: 'Nueva2027' });
  });
});
