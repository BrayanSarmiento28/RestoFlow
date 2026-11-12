// Pruebas con Jest · Dashboard (resumen del día del administrador, inventario y accesos según el rol).
import { Inicio } from './inicio';
import { PanelService } from '../../core/services/panel.service';
import { PedidoService } from '../../core/services/pedido.service';
import { InventarioService } from '../../core/services/inventario.service';
import { RestoFlowAIService } from '../../core/services/restoflow-ai.service';
import { Rol } from '../../core/entities';
import { Montaje, conSesion, crear, falla, ok } from '../../../testing/angular';
import { alerta, analisis, ingrediente, pedido, resumenPanel } from '../../../testing/datos';

function apis() {
  return {
    panel: { resumen: jest.fn(() => ok(resumenPanel())) },
    pedidos: { consultar: jest.fn(() => ok([pedido({ id: 2, numeroMesa: 9, estadoCocina: 'LISTO' }), pedido({ numeroMesa: 3 })])) },
    inventario: {
      ingredientes: jest.fn(() => ok([ingrediente({ nombre: 'Arroz', enMinimo: true }), ingrediente({ id: 2, nombre: 'Sal' })])),
      alertas: jest.fn(() => ok([alerta(), alerta({ id: 2 })])),
    },
    ia: { analisis: jest.fn(() => ok(analisis())) },
  };
}

describe('Dashboard (Inicio)', () => {
  let montaje: Montaje<Inicio>;
  let api: ReturnType<typeof apis>;

  function abrir(rol: Rol, preparar?: (a: ReturnType<typeof apis>) => void) {
    api = apis();
    preparar?.(api);
    montaje = crear(Inicio, [
      { provide: PanelService, useValue: api.panel },
      { provide: PedidoService, useValue: api.pedidos },
      { provide: InventarioService, useValue: api.inventario },
      { provide: RestoFlowAIService, useValue: api.ia },
      conSesion(rol),
    ]);
    montaje.componente.ngOnInit();
    return montaje.componente;
  }

  afterEach(() => {
    montaje.componente.ngOnDestroy();
    montaje.destruir();
  });

  it('el administrador ve el resumen del día, las mesas abiertas y las recomendaciones de RestoFlow AI', () => {
    const c = abrir('ADMIN');
    expect(api.pedidos.consultar).toHaveBeenCalledWith({ estadoPedido: 'ABIERTO' });
    expect(c['resumen']()?.ventas).toBe(150000);
    expect(c['variacion']()).toBe(50);
    expect(c['mesas']().map((m) => m.mesa)).toEqual([3, 9]);
    expect(c['recomendacionesIA']()?.map((r) => r.titulo)).toEqual(['Comprar arroz', 'Sube la bandeja']);
    expect(c['accesos']).toHaveLength(6);
    expect(c['horas']().length).toBeGreaterThan(0);
    expect(c['maximoHora']()).toBe(150000);
    expect(api.inventario.ingredientes).not.toHaveBeenCalled();
  });

  it('si RestoFlow AI no responde, no hay recomendaciones (se muestran los insumos bajos)', () => {
    const c = abrir('ADMIN', (a) => a.ia.analisis.mockReturnValue(falla(503)));
    expect(c['recomendacionesIA']()).toBeNull();
  });

  it('si el resumen falla, muestra el error', () => {
    const c = abrir('ADMIN', (a) => a.panel.resumen.mockReturnValue(falla(0)));
    expect(c['error']()).toContain('No se pudo conectar');
    expect(c['variacion']()).toBeNull();
  });

  it('el administrador ve el resumen actualizado cada pocos segundos', () => {
    jest.useFakeTimers();
    try {
      abrir('ADMIN');
      jest.advanceTimersByTime(10_500);
      expect(api.panel.resumen.mock.calls.length).toBeGreaterThan(1);
    } finally {
      montaje.componente.ngOnDestroy();
      jest.useRealTimers();
    }
  });

  it('el personal de inventario ve sus insumos en el mínimo y las alertas pendientes', () => {
    const c = abrir('INVENTARIO');
    expect(api.inventario.ingredientes).toHaveBeenCalledWith(true);
    expect(c['insumosBajos']().map((i) => i.nombre)).toEqual(['Arroz']);
    expect(c['alertasPendientes']()).toBe(2);
    expect(api.panel.resumen).not.toHaveBeenCalled();
    expect(c['accesos'].map((a) => a.ruta)).toEqual(['/inventario']);
  });

  it('si el inventario falla, muestra el error', () => {
    const c = abrir('INVENTARIO', (a) => a.inventario.alertas.mockReturnValue(falla(500, 'Error de alertas')));
    expect(c['error']()).toBe('Error de alertas');
  });

  it('el mesero solo ve sus accesos directos (no consulta resúmenes)', () => {
    const c = abrir('MESERO');
    expect(c['accesos'].map((a) => a.ruta)).toEqual(['/pedidos']);
    expect(api.panel.resumen).not.toHaveBeenCalled();
    expect(api.inventario.ingredientes).not.toHaveBeenCalled();
    expect(c['hoy'].length).toBeGreaterThan(0);
  });
});
