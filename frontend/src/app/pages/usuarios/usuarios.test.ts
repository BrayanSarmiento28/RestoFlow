// Pruebas con Jest · cuentas del personal (solo el administrador las crea, desactiva y reactiva).
import { throwError } from 'rxjs';
import { Usuarios } from './usuarios';
import { UsuarioService } from '../../core/services/usuario.service';
import { Montaje, conSesion, crear, falla, ok } from '../../../testing/angular';

// La sesión de prueba es la del administrador con id 7; Laura es otra cuenta
const laura = { id: 8, nombre: 'Laura Gómez', email: 'laura@restoflow.com', rol: 'MESERO' as const, activo: true };

describe('Cuentas del personal', () => {
  let montaje: Montaje<Usuarios>;
  let api: { listar: jest.Mock; crear: jest.Mock; cambiarEstado: jest.Mock };

  function abrir() {
    api = { listar: jest.fn(() => ok([laura])), crear: jest.fn(() => ok({ ...laura, id: 9, nombre: 'Pedro Ruiz', rol: 'COCINA' })),
      cambiarEstado: jest.fn((_id: number, activa: boolean) => ok({ ...laura, activo: activa })) };
    montaje = crear(Usuarios, [{ provide: UsuarioService, useValue: api }, conSesion('ADMIN')]);
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

  it('desactiva otra cuenta después de confirmar en la ventana', () => {
    const c = abrir();
    c['cambiarEstado'](laura);
    expect(c['porCambiarEstado']()).toEqual(laura);
    expect(api.cambiarEstado).not.toHaveBeenCalled();

    c['confirmarCambioEstado']();
    expect(api.cambiarEstado).toHaveBeenCalledWith(8, false);
    expect(c['porCambiarEstado']()).toBeNull();
    expect(c['cambiandoEstado']()).toBe(false);
    expect(c['exito']()).toBe('La cuenta de Laura Gómez se desactivó. Ya no puede iniciar sesión.');
    expect(api.listar).toHaveBeenCalledTimes(2);
  });

  it('reactiva una cuenta inactiva', () => {
    const c = abrir();
    c['cambiarEstado']({ ...laura, activo: false });
    c['confirmarCambioEstado']();
    expect(api.cambiarEstado).toHaveBeenCalledWith(8, true);
    expect(c['exito']()).toBe('La cuenta de Laura Gómez se reactivó. Ya puede iniciar sesión.');
  });

  it('reconoce la cuenta propia para no ofrecer desactivarla', () => {
    const c = abrir();
    expect(c['esMiCuenta']({ ...laura, id: 7 })).toBe(true);
    expect(c['esMiCuenta'](laura)).toBe(false);
  });

  it('Volver o Escape cierran la ventana sin cambiar nada; confirmar sin cuenta no hace nada', () => {
    const c = abrir();
    c['cambiarEstado'](laura);
    c['cerrarVentana']();
    expect(c['porCambiarEstado']()).toBeNull();
    c['confirmarCambioEstado']();
    expect(api.cambiarEstado).not.toHaveBeenCalled();
  });

  it('si el backend rechaza el cambio muestra su mensaje y cierra la ventana', () => {
    const c = abrir();
    api.cambiarEstado.mockReturnValue(falla(409, 'No puedes desactivar tu propia cuenta'));
    c['cambiarEstado'](laura);
    c['confirmarCambioEstado']();
    expect(c['error']()).toBe('No puedes desactivar tu propia cuenta');
    expect(c['porCambiarEstado']()).toBeNull();
    expect(c['cambiandoEstado']()).toBe(false);
  });

  it('por defecto muestra solo las activas; las inactivas y todas se ven con el filtro', () => {
    const c = abrir();
    const pedro = { ...laura, id: 9, nombre: 'Pedro Ruiz', activo: false };
    c['usuarios'].set([laura, pedro]);
    expect(c['filtro']()).toBe('activas');
    expect(c['visibles']()).toEqual([laura]);
    expect(c['cuenta']()).toEqual({ activas: 1, inactivas: 1, todas: 2 });

    c['filtro'].set('inactivas');
    expect(c['visibles']()).toEqual([pedro]);
    c['filtro'].set('todas');
    expect(c['visibles']()).toEqual([laura, pedro]);
  });
});
