// Pruebas con Jest · recuperar la contraseña en 2 pasos (pedir el código y cambiar la contraseña).
import { RecuperarContrasena } from './recuperar-contrasena';
import { AuthService } from '../../core/services/auth.service';
import { Montaje, crear, falla, ok, sesionFalsa } from '../../../testing/angular';

describe('Recuperar contraseña', () => {
  let montaje: Montaje<RecuperarContrasena>;
  let auth: ReturnType<typeof sesionFalsa>;

  function abrir() {
    auth = sesionFalsa(null);
    auth.recuperar.mockReturnValue(ok({ mensaje: 'Si el correo existe, enviamos un código.' }));
    auth.restablecer.mockReturnValue(ok({ mensaje: 'Contraseña actualizada.' }));
    montaje = crear(RecuperarContrasena, [{ provide: AuthService, useValue: auth }]);
    return montaje.componente;
  }

  afterEach(() => montaje.destruir());

  it('paso 1: valida el correo y pide el código', () => {
    const c = abrir();
    c['enviarCodigo']();
    expect(auth.recuperar).not.toHaveBeenCalled();
    expect(c['invalido'](c['formCorreo'].controls.email)).toBe(true);

    c['formCorreo'].controls.email.setValue('laura@restoflow.com');
    c['enviarCodigo']();
    expect(auth.recuperar).toHaveBeenCalledWith('laura@restoflow.com');
    expect(c['paso']()).toBe(2);
    expect(c['mensaje']()).toBe('Si el correo existe, enviamos un código.');
    expect(c['cargando']()).toBe(false);
  });

  it('paso 2: exige un código de 6 dígitos y que las contraseñas coincidan', () => {
    const c = abrir();
    c['formCorreo'].controls.email.setValue('laura@restoflow.com');
    c['enviarCodigo']();
    c['restablecer']();
    expect(auth.restablecer).not.toHaveBeenCalled();

    c['formCodigo'].setValue({ codigo: '123456', nuevaPassword: 'Nueva2026', confirmar: 'Otra2026' });
    expect(c['formCodigo'].hasError('noCoinciden')).toBe(true);
    c['restablecer']();
    expect(auth.restablecer).not.toHaveBeenCalled();

    c['formCodigo'].controls.confirmar.setValue('Nueva2026');
    c['restablecer']();
    expect(auth.restablecer).toHaveBeenCalledWith('laura@restoflow.com', '123456', 'Nueva2026');
    expect(c['paso']()).toBe(3);
    expect(c['mensaje']()).toBe('Contraseña actualizada.');
  });

  it('cambiar el correo vuelve al paso 1 y limpia el formulario', () => {
    const c = abrir();
    c['formCorreo'].controls.email.setValue('laura@restoflow.com');
    c['enviarCodigo']();
    c['formCodigo'].controls.codigo.setValue('123456');
    c['cambiarCorreo']();
    expect(c['paso']()).toBe(1);
    expect(c['formCodigo'].controls.codigo.value).toBe('');
    expect(c['mensaje']()).toBeNull();
  });

  it('muestra el error del backend o el de conexión', () => {
    const c = abrir();
    c['formCorreo'].controls.email.setValue('laura@restoflow.com');
    auth.recuperar.mockReturnValue(falla(0));
    c['enviarCodigo']();
    expect(c['error']()).toContain('No se pudo conectar');
    expect(c['cargando']()).toBe(false);

    auth.recuperar.mockReturnValue(ok({ mensaje: 'Enviado' }));
    c['enviarCodigo']();
    c['formCodigo'].setValue({ codigo: '000000', nuevaPassword: 'Nueva2026', confirmar: 'Nueva2026' });
    auth.restablecer.mockReturnValue(falla(400, 'El código no es válido o ya venció.'));
    c['restablecer']();
    expect(c['error']()).toBe('El código no es válido o ya venció.');
    auth.restablecer.mockReturnValue(falla(500));
    c['restablecer']();
    expect(c['error']()).toBe('No se pudo completar la solicitud. Intenta de nuevo.');
  });
});
