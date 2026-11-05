// Pruebas con Jest · cuentas del personal (solo el administrador las crea).
import { throwError } from 'rxjs';
import { Usuarios } from './usuarios';
import { UsuarioService } from '../../core/services/usuario.service';
import { Montaje, crear, falla, ok } from '../../../testing/angular';

const laura = { id: 7, nombre: 'Laura Gómez', email: 'laura@restoflow.com', rol: 'MESERO' as const, activo: true };

describe('Cuentas del personal', () => {
  let montaje: Montaje<Usuarios>;
  let api: { listar: jest.Mock; crear: jest.Mock };

  function abrir() {
    api = { listar: jest.fn(() => ok([laura])), crear: jest.fn(() => ok({ ...laura, id: 8, nombre: 'Pedro Ruiz', rol: 'COCINA' })) };
    montaje = crear(Usuarios, [{ provide: UsuarioService, useValue: api }]);
    montaje.componente.ngOnInit();
    return montaje.componente;
  }

  afterEach(() => montaje.destruir());

  it('lista las cuentas al abrir', () => {
    const c = abrir();
    expect(c['usuarios']()).toEqual([laura]);
    expect(c['nombreRol']('ADMIN')).toBe('Administrador');
  });

  it('valida el formulario y crea la cuenta con el rol elegido', () => {
    const c = abrir();
    c['crear']();
    expect(api.crear).not.toHaveBeenCalled();
    expect(c['invalido']('nombre')).toBe(true);

    c['form'].setValue({ nombre: ' Pedro Ruiz ', email: 'pedro@restoflow.com', password: 'Cocina2026', rol: 'COCINA' });
    c['crear']();
    expect(api.crear).toHaveBeenCalledWith({ nombre: 'Pedro Ruiz', email: 'pedro@restoflow.com', password: 'Cocina2026', rol: 'COCINA' });
    expect(c['exito']()).toBe('Cuenta de Pedro Ruiz creada con el rol Cocina.');
    expect(c['form'].controls.nombre.value).toBe('');
    expect(api.listar).toHaveBeenCalledTimes(2);
  });

  it('explica cada error: sin conexión, sin permiso, campos inválidos o mensaje del backend', () => {
    const c = abrir();
    c['form'].setValue({ nombre: 'Pedro Ruiz', email: 'pedro@restoflow.com', password: 'Cocina2026', rol: 'COCINA' });
    const casos: [ReturnType<typeof falla>, string][] = [
      [falla(0), 'No se pudo conectar con el servidor. Verifica que el backend esté encendido.'],
      [falla(403), 'Solo un administrador puede gestionar cuentas.'],
      [falla(409, 'Ya existe una cuenta con ese correo'), 'Ya existe una cuenta con ese correo'],
      [falla(500), 'No se pudo completar la solicitud.'],
    ];
    for (const [respuesta, mensaje] of casos) {
      api.crear.mockReturnValue(respuesta);
      c['crear']();
      expect(c['error']()).toBe(mensaje);
      expect(c['cargando']()).toBe(false);
    }
  });

  it('une los mensajes de los campos inválidos que envía el backend', () => {
    const c = abrir();
    api.listar.mockReturnValue(throwError(() => ({ status: 400, error: { campos: { email: 'Correo inválido', password: 'Muy corta' } } })));
    c.ngOnInit();
    expect(c['error']()).toBe('Correo inválido. Muy corta');
  });
});
