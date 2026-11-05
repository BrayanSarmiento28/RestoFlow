// Pruebas con Jest · campana de avisos de RestoFlow AI (consulta cada minuto, resumen de Gemini al abrirla).
import { ElementRef } from '@angular/core';
import { AvisosCampana } from './avisos-campana';
import { AvisosService } from '../../core/services/avisos.service';
import { Rol } from '../../core/entities';
import { Montaje, conSesion, crear, falla, ok } from '../../../testing/angular';
import { aviso } from '../../../testing/datos';

const urgente = aviso({ clave: 'A', nivel: 'URGENTE', enlace: '/pedidos' });
const informativo = aviso({ clave: 'B', nivel: 'INFO', enlace: '/inventario' });

describe('Campana de avisos', () => {
  let montaje: Montaje<AvisosCampana>;
  let api: { consultar: jest.Mock; resumen: jest.Mock };
  const dentro = { id: 'campana' };

  function abrir(rol: Rol = 'MESERO', avisos = [urgente, informativo]) {
    jest.useFakeTimers();
    api = {
      consultar: jest.fn(() => ok({ generadoEn: '', avisos })),
      resumen: jest.fn(() => ok({ texto: 'Atienda primero la mesa 3.', origen: 'GEMINI' as const, generadoEn: '' })),
    };
    montaje = crear(AvisosCampana, [
      { provide: AvisosService, useValue: api },
      { provide: ElementRef, useValue: { nativeElement: { contains: (n: unknown) => n === dentro } } },
      conSesion(rol),
    ]);
    montaje.componente.ngOnInit();
    jest.advanceTimersByTime(1);
    return montaje.componente;
  }

  afterEach(() => {
    montaje.destruir();
    jest.useRealTimers();
  });

  it('consulta los avisos al empezar y cada minuto', () => {
    const c = abrir();
    expect(c['avisos']()).toHaveLength(2);
    expect(c['sinVer']()).toBe(1);
    expect(c['urgente']()).toBe(true);
    expect(c['sinConexion']()).toBe(false);
    jest.advanceTimersByTime(60_000);
    expect(api.consultar).toHaveBeenCalledTimes(2);
  });

  it('si no hay conexión lo indica y conserva los avisos anteriores', () => {
    const c = abrir();
    api.consultar.mockReturnValue(falla(0));
    jest.advanceTimersByTime(60_000);
    expect(c['sinConexion']()).toBe(true);
    expect(c['avisos']()).toHaveLength(2);
  });

  it('al abrirla marca todo como visto y pide el resumen de Gemini', () => {
    const c = abrir();
    c['alternar']();
    expect(c['abierto']()).toBe(true);
    expect(c['sinVer']()).toBe(0);
    expect(c['urgente']()).toBe(false);
    expect(api.resumen).toHaveBeenCalled();
    expect(c['resumen']()?.texto).toBe('Atienda primero la mesa 3.');
    expect(c['pensando']()).toBe(false);
    c['alternar']();
    expect(c['abierto']()).toBe(false);
  });

  it('sin avisos no pide resumen; si el resumen falla, la campana sigue funcionando', () => {
    let c = abrir('MESERO', []);
    c['alternar']();
    expect(api.resumen).not.toHaveBeenCalled();
    montaje.destruir();
    jest.useRealTimers();

    c = abrir();
    api.resumen.mockReturnValue(falla(503));
    c['alternar']();
    expect(c['resumen']()).toBeNull();
    expect(c['pensando']()).toBe(false);
  });

  it('solo muestra el enlace si el rol puede abrir esa pantalla', () => {
    const c = abrir('MESERO');
    expect(c['enlace'](urgente)).toBe('/pedidos');
    expect(c['enlace'](informativo)).toBeNull();
  });

  it('se cierra con Escape o con un clic fuera de la campana', () => {
    const c = abrir();
    c['alternar']();
    c['alHacerClic']({ target: dentro } as unknown as MouseEvent);
    expect(c['abierto']()).toBe(true);
    c['alHacerClic']({ target: {} } as unknown as MouseEvent);
    expect(c['abierto']()).toBe(false);
    c['alternar']();
    c['alPresionarEscape']();
    expect(c['abierto']()).toBe(false);
  });
});
