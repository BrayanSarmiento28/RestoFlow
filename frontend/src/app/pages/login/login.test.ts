// Pruebas con Jest · iniciar sesión.
import { Router } from '@angular/router';
import { Login } from './login';
import { AuthService } from '../../core/services/auth.service';
import { Montaje, crear, falla, ok, sesionFalsa } from '../../../testing/angular';

describe('Iniciar sesión', () => {
  let montaje: Montaje<Login>;
  let auth: ReturnType<typeof sesionFalsa>;
  let router: { navigate: jest.Mock };

  function abrir() {
    auth = sesionFalsa(null);
    auth.login.mockReturnValue(ok({}));
    router = { navigate: jest.fn() };
    montaje = crear(Login, [{ provide: AuthService, useValue: auth }, { provide: Router, useValue: router }]);
    return montaje.componente;
  }

  afterEach(() => montaje.destruir());

  it('no envía si faltan el correo o la contraseña', () => {
    const c = abrir();
    c['ingresar']();
    expect(auth.login).not.toHaveBeenCalled();
    expect(c['invalido']('email')).toBe(true);
    expect(c['invalido']('password')).toBe(true);
  });

  it('con datos válidos inicia sesión (con "Recordarme") y entra al Dashboard', () => {
    const c = abrir();
    c['form'].setValue({ email: 'admin@restoflow.com', password: 'Admin2026*', recordarme: true });
    c['ingresar']();
    expect(auth.login).toHaveBeenCalledWith({ email: 'admin@restoflow.com', password: 'Admin2026*' }, true);
    expect(router.navigate).toHaveBeenCalledWith(['/inicio']);
  });

  it('muestra por qué no pudo entrar', () => {
    const c = abrir();
    c['form'].setValue({ email: 'admin@restoflow.com', password: 'mala', recordarme: false });
    auth.login.mockReturnValue(falla(401, 'Correo o contraseña incorrectos'));
    c['ingresar']();
    expect(c['error']()).toBe('Correo o contraseña incorrectos');
    expect(c['cargando']()).toBe(false);
    auth.login.mockReturnValue(falla(0));
    c['ingresar']();
    expect(c['error']()).toContain('No se pudo conectar');
    auth.login.mockReturnValue(falla(500));
    c['ingresar']();
    expect(c['error']()).toBe('Correo o contraseña incorrectos');
  });
});
