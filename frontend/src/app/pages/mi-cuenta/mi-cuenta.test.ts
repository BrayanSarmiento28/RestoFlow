// Pruebas con Jest · Mi cuenta: datos del usuario y cambio de contraseña escribiendo la actual.
import { MiCuenta } from './mi-cuenta';
import { AuthService } from '../../core/services/auth.service';
import { Montaje, crear, falla, ok, sesionFalsa } from '../../../testing/angular';

describe('Mi cuenta', () => {
  let montaje: Montaje<MiCuenta>;
  let auth: ReturnType<typeof sesionFalsa> & { cambiarContrasena: jest.Mock };

  function abrir(rol: 'ADMIN' | 'COCINA' | null = 'COCINA') {
    auth = { ...sesionFalsa(rol), cambiarContrasena: jest.fn(() => ok({ mensaje: 'Tu contraseña se cambió correctamente.' })) };
    montaje = crear(MiCuenta, [{ provide: AuthService, useValue: auth }]);
    return montaje.componente;
  }

  function llenar(c: MiCuenta, actual: string, nueva: string, confirmar = nueva) {
    c['form'].setValue({ actual, nueva, confirmar });
  }

  afterEach(() => montaje.destruir());

  it('muestra los datos del usuario de la sesión con el nombre de su rol', () => {
    const c = abrir('COCINA');
    expect(c['usuario']?.nombre).toBe('Laura Gómez');
    expect(c['nombreRol']).toBe('Cocina');
    montaje.destruir();
    expect(abrir(null)['nombreRol']).toBe('');
  });

  it('no envía si faltan datos, si la nueva no es segura o si la confirmación no coincide', () => {
    const c = abrir();
    c['cambiar']();
    expect(c['invalido']('actual')).toBe(true);
    expect(auth.cambiarContrasena).not.toHaveBeenCalled();

    llenar(c, 'Cocina2026', 'corta');
    c['cambiar']();
    expect(c['invalido']('nueva')).toBe(true);

    llenar(c, 'Cocina2026', 'Fogon2027', 'Fogon2028');
    c['form'].controls.confirmar.markAsTouched();
    expect(c['noCoinciden']()).toBe(true);
    c['cambiar']();
    expect(auth.cambiarContrasena).not.toHaveBeenCalled();
  });

  it('la nueva debe ser distinta de la actual', () => {
    const c = abrir();
    llenar(c, 'Cocina2026', 'Cocina2026');
    c['cambiar']();
    expect(c['error']()).toBe('La nueva contraseña debe ser distinta de la actual.');
    expect(auth.cambiarContrasena).not.toHaveBeenCalled();
  });

  it('cambia la contraseña, muestra el mensaje del backend y limpia el formulario', () => {
    const c = abrir();
    llenar(c, 'Cocina2026', 'Fogon2027');
    c['cambiar']();
    expect(auth.cambiarContrasena).toHaveBeenCalledWith('Cocina2026', 'Fogon2027');
    expect(c['exito']()).toBe('Tu contraseña se cambió correctamente.');
    expect(c['form'].controls.actual.value).toBe('');
    expect(c['guardando']()).toBe(false);
  });

  it('si la contraseña actual no es correcta, muestra el error del backend', () => {
    const c = abrir();
    auth.cambiarContrasena.mockReturnValue(falla(400, 'La contraseña actual no es correcta'));
    llenar(c, 'Otra2026', 'Fogon2027');
    c['cambiar']();
    expect(c['error']()).toBe('La contraseña actual no es correcta');
    expect(c['exito']()).toBeNull();
    expect(c['guardando']()).toBe(false);
  });
});
