// Pruebas con Jest · pantalla de Reportes (historial de ventas, pedidos y movimientos por rango de fechas).
import { Reportes } from './reportes';
import { PanelService } from '../../core/services/panel.service';
import { InventarioService } from '../../core/services/inventario.service';
import { fechaIso } from '../../core/util/formato';
import { Montaje, crear, falla, ok } from '../../../testing/angular';
import { ingrediente, movimiento, pedido } from '../../../testing/datos';

function apis() {
  return {
    panel: {
      ventas: jest.fn(() => ok({
        desde: '2026-11-01', hasta: '2026-11-05', total: 300000, cantidad: 3, ticketPromedio: 100000, ventas: [],
        tiemposPreparacion: [{ productoId: 1, nombre: 'Bandeja paisa', pedidos: 4, medianaMinutos: 24, promedioMinutos: 47 }],
        porDia: [{ dia: '2026-11-04', total: 100000, cantidad: 1 }, { dia: '2026-11-05', total: 200000, cantidad: 2 }],
      })),
      pedidos: jest.fn(() => ok([pedido({ total: 64000 }), pedido({ id: 41, total: 36000 })])),
    },
    inventario: {
      ingredientes: jest.fn(() => ok([ingrediente()])),
      movimientos: jest.fn(() => ok([movimiento()])),
    },
  };
}

describe('Pantalla de Reportes', () => {
  let montaje: Montaje<Reportes>;
  let api: ReturnType<typeof apis>;

  function abrir() {
    api = apis();
    montaje = crear(Reportes, [
      { provide: PanelService, useValue: api.panel },
      { provide: InventarioService, useValue: api.inventario },
    ]);
    montaje.componente.ngOnInit();
    return montaje.componente;
  }

  afterEach(() => montaje.destruir());

  it('al abrir consulta las ventas de los últimos 7 días y carga los insumos para el filtro', () => {
    const c = abrir();
    const hoy = fechaIso(new Date());
    expect(c['hasta']()).toBe(hoy);
    expect(api.panel.ventas).toHaveBeenCalledWith(c['desde'](), hoy);
    expect(c['insumos']()).toHaveLength(1);
    expect(c['cargando']()).toBe(false);
  });

  it('RF-09 · recibe el tiempo de preparación de cada plato del periodo', () => {
    const c = abrir();
    expect(c['ventas']()?.tiemposPreparacion).toEqual([
      { productoId: 1, nombre: 'Bandeja paisa', pedidos: 4, medianaMinutos: 24, promedioMinutos: 47 },
    ]);
  });

  it('calcula la altura de cada barra frente al mejor día', () => {
    const c = abrir();
    const barras = c['barras']();
    expect(barras.map((b) => b.alto)).toEqual([50, 100]);
    expect(barras[0].etiqueta.length).toBeGreaterThan(0);
  });

  it('sin ventas no hay barras', () => {
    const c = abrir();
    c['ventas'].set(null);
    expect(c['barras']()).toEqual([]);
  });

  it('la pestaña Pedidos consulta con el estado elegido y suma el total', () => {
    const c = abrir();
    c['estado'].set('PAGADO');
    c['cambiarPestana']('pedidos');
    expect(api.panel.pedidos).toHaveBeenCalledWith(c['desde'](), c['hasta'](), 'PAGADO');
    expect(c['totalPedidos']()).toBe(100000);
    expect(c['productos'](pedido())).toBe('2× Bandeja paisa');
  });

  it('la pestaña Movimientos envía solo los filtros elegidos', () => {
    const c = abrir();
    c['cambiarPestana']('movimientos');
    expect(api.inventario.movimientos).toHaveBeenLastCalledWith({
      desde: c['desde'](), hasta: c['hasta'](), tipo: undefined, ingredienteId: undefined,
    });
    c['tipo'].set('SALIDA');
    c['insumoId'].set(1);
    c['consultar']();
    expect(api.inventario.movimientos).toHaveBeenLastCalledWith({ desde: c['desde'](), hasta: c['hasta'](), tipo: 'SALIDA', ingredienteId: 1 });
    expect(c['movimientos']()).toHaveLength(1);
    expect(c['cantidad'](movimiento({ tipo: 'SALIDA', cantidad: 200 }))).toBe('−200 g');
  });

  it('los rangos rápidos cambian las fechas y consultan de nuevo', () => {
    const c = abrir();
    c['rangoRapido'](1);
    expect(c['desde']()).toBe(fechaIso(new Date()));
    expect(api.panel.ventas).toHaveBeenCalledTimes(2);
  });

  it('sin fechas no consulta; si el API falla, muestra el error', () => {
    const c = abrir();
    c['desde'].set('');
    c['consultar']();
    expect(api.panel.ventas).toHaveBeenCalledTimes(1);
    c['desde'].set('2026-11-01');
    api.panel.ventas.mockReturnValue(falla(400, 'La fecha inicial no puede ser posterior a la final'));
    c['consultar']();
    expect(c['error']()).toBe('La fecha inicial no puede ser posterior a la final');
    expect(c['cargando']()).toBe(false);
  });
});
