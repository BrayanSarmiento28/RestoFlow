// Pruebas con Jest · pantalla de Cocina (comandas por estado y avance de cada pedido).
import { Cocina } from './cocina';
import { CocinaService } from '../../core/services/cocina.service';
import { Montaje, conSesion, crear, falla, ok } from '../../../testing/angular';
import { pedido } from '../../../testing/datos';

const pendiente = pedido({ id: 1, estadoCocina: 'PENDIENTE' });
const preparando = pedido({ id: 2, estadoCocina: 'EN_PREPARACION' });
const listo = pedido({ id: 3, estadoCocina: 'LISTO' });

describe('Pantalla de Cocina', () => {
  let montaje: Montaje<Cocina>;
  let api: { comandas: jest.Mock; cambiarEstado: jest.Mock };

  function abrir(rol: 'COCINA' | 'ADMIN' = 'COCINA') {
    api = { comandas: jest.fn(() => ok([pendiente, preparando, listo])), cambiarEstado: jest.fn(() => ok(listo)) };
    montaje = crear(Cocina, [{ provide: CocinaService, useValue: api }, conSesion(rol)]);
    montaje.componente.ngOnInit();
    return montaje.componente;
  }

  afterEach(() => {
    montaje.componente.ngOnDestroy();
    montaje.destruir();
  });

  it('muestra las comandas agrupadas por estado', () => {
    const c = abrir();
    expect(c['total']()).toBe(3);
    expect(c['deEstado']('PENDIENTE').map((p) => p.id)).toEqual([1]);
    expect(c['deEstado']('LISTO').map((p) => p.id)).toEqual([3]);
    expect(c['actualizado']()).not.toBeNull();
    expect(c['verNumero']).toBe(false);
    expect(c['minutos'](pendiente)).toBeGreaterThanOrEqual(0);
  });

  it('con una adición, el tiempo de la comanda cuenta desde la adición', () => {
    const c = abrir();
    const haceUnaHora = new Date(Date.now() - 60 * 60_000).toISOString();
    const recien = new Date(Date.now() - 2 * 60_000).toISOString();
    expect(c['minutos'](pedido({ fechaCreacion: haceUnaHora }))).toBeGreaterThanOrEqual(59);
    expect(c['minutos'](pedido({ fechaCreacion: haceUnaHora, fechaAdicion: recien }))).toBeLessThan(5);
  });

  it('el botón de cada pedido dice el siguiente paso', () => {
    const c = abrir('ADMIN');
    expect(c['verNumero']).toBe(true);
    expect(c['accion'](pendiente)).toBe('Iniciar preparación');
    expect(c['accion'](preparando)).toBe('Marcar listo');
    expect(c['accion'](listo)).toBe('Marcar entregado');
    expect(c['accion'](pedido({ estadoCocina: 'ENTREGADO' }))).toBe('');
  });

  it('avanza el pedido al siguiente estado y recarga', () => {
    const c = abrir();
    c['avanzar'](preparando);
    expect(api.cambiarEstado).toHaveBeenCalledWith(2, 'LISTO');
    expect(c['procesando']()).toBeNull();
    expect(api.comandas).toHaveBeenCalledTimes(2);
  });

  it('un pedido entregado no avanza más', () => {
    const c = abrir();
    c['avanzar'](pedido({ estadoCocina: 'ENTREGADO' }));
    expect(api.cambiarEstado).not.toHaveBeenCalled();
  });

  it('si el cambio de estado falla, muestra el error y recarga para ver el estado real', () => {
    const c = abrir();
    api.cambiarEstado.mockReturnValue(falla(409, 'El pedido fue cancelado'));
    api.comandas.mockReturnValue(falla(0));
    c['avanzar'](pendiente);
    expect(c['procesando']()).toBeNull();
    expect(c['error']()).toContain('No se pudo conectar');
  });

  it('se actualiza sola cada pocos segundos', () => {
    jest.useFakeTimers();
    try {
      const c = abrir();
      jest.advanceTimersByTime(2600);
      expect(api.comandas.mock.calls.length).toBeGreaterThan(1);
      c.ngOnDestroy();
    } finally {
      jest.useRealTimers();
    }
  });
});
