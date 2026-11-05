// Pruebas con Jest · pantalla de Inventario (entradas, salidas, ajustes, insumos, código de barras y alertas).
import { Inventario } from './inventario';
import { InventarioService } from '../../core/services/inventario.service';
import { fechaIso } from '../../core/util/formato';
import { Montaje, conSesion, crear, falla, ok, simularNavegador } from '../../../testing/angular';
import { alerta, ingrediente, movimiento } from '../../../testing/datos';

const arroz = ingrediente({ id: 1, nombre: 'Arroz', stockActual: 5000, stockMinimo: 1000 });
const huevo = ingrediente({ id: 2, nombre: 'Huevo', unidad: 'UNIDAD', stockActual: 0, stockMinimo: 12, agotado: true, enMinimo: true });
const leche = ingrediente({ id: 3, nombre: 'Leche', unidad: 'LITRO', stockActual: 1, stockMinimo: 4, enMinimo: true });
const viejo = ingrediente({ id: 4, nombre: 'Panela', activo: false });

function apiInventario() {
  const hoy = `${fechaIso(new Date())}T10:00:00`;
  return {
    ingredientes: jest.fn(() => ok([arroz, huevo, leche, viejo])),
    movimientos: jest.fn(() => ok([
      movimiento({ id: 1, fecha: hoy, tipo: 'SALIDA', pedidoId: 12, usuarioNombre: 'Sistema', motivo: null }),
      movimiento({ id: 2, fecha: hoy, tipo: 'ENTRADA' }),
      movimiento({ id: 3, fecha: '2026-01-01T10:00:00' }),
    ])),
    alertas: jest.fn(() => ok([alerta({ id: 1 }), alerta({ id: 2, ingredienteNombre: 'Leche' })])),
    entrada: jest.fn(() => ok({ ...arroz, stockActual: 5500 })),
    salida: jest.fn(() => ok({ ...arroz, stockActual: 4500 })),
    ajuste: jest.fn(() => ok({ ...arroz, stockActual: 4800 })),
    crear: jest.fn(() => ok(ingrediente({ id: 9, nombre: 'Gaseosa 350 ml' }))),
    editar: jest.fn(() => ok({ ...arroz, nombre: 'Arroz blanco' })),
    cambiarEstado: jest.fn((id: number, activo: boolean) => ok({ ...arroz, activo })),
    atender: jest.fn(() => ok(alerta({ atendida: true }))),
    buscarPorCodigoBarras: jest.fn(() => ok({
      codigoBarras: '7702004003508', nombre: 'Gaseosa', marca: 'Postobón', cantidad: '350 ml', imagenUrl: null,
      fuente: 'Open Food Facts', nombreSugerido: 'Gaseosa 350 ml', unidadSugerida: 'UNIDAD' as const,
    })),
  };
}

describe('Pantalla de Inventario', () => {
  let montaje: Montaje<Inventario>;
  let api: ReturnType<typeof apiInventario>;
  let navegador: ReturnType<typeof simularNavegador>;

  function abrir(rol: 'ADMIN' | 'INVENTARIO' = 'INVENTARIO') {
    navegador = simularNavegador();
    api = apiInventario();
    montaje = crear(Inventario, [{ provide: InventarioService, useValue: api }, conSesion(rol)]);
    montaje.componente.ngOnInit();
    return montaje.componente;
  }

  afterEach(() => {
    montaje.destruir();
    navegador.restaurar();
  });

  it('carga insumos, movimientos de la última semana y alertas, y los ordena por urgencia', () => {
    const c = abrir();
    expect(api.movimientos.mock.calls[0]).toHaveLength(1);
    expect(api.alertas).toHaveBeenCalledWith(true);
    expect(c['filas']().map((f) => f.insumo.nombre)).toEqual(['Huevo', 'Leche', 'Arroz']);
    expect(c['inactivos']().map((i) => i.nombre)).toEqual(['Panela']);
    expect(c['urgente']()?.insumo.nombre).toBe('Huevo');
    expect(c['bajoMinimo']()).toHaveLength(2);
    expect(c['movimientosHoy']()).toEqual({ total: 2, ventas: 1, entradas: 1 });
    expect(c['recientes']()).toHaveLength(3);
    expect(c['actualizado']()).not.toBeNull();
    expect(c['esAdmin']).toBe(false);
    expect(c['unidades']).toContain('KILOGRAMO');
  });

  it('el filtro "con alerta" muestra solo los insumos agotados, en mínimo o por acabarse', () => {
    const c = abrir();
    c['filtro'].set('alerta');
    expect(c['visibles']().map((f) => f.insumo.nombre)).toEqual(['Huevo', 'Leche']);
    expect(c['nombres'](c['visibles']())).toBe('Huevo, Leche');
    expect(c['porAgotarse']().length).toBeGreaterThanOrEqual(0);
  });

  it('si el API no responde, muestra el error', () => {
    navegador = simularNavegador();
    api = apiInventario();
    api.ingredientes.mockReturnValue(falla(0));
    montaje = crear(Inventario, [{ provide: InventarioService, useValue: api }, conSesion('ADMIN')]);
    montaje.componente.ngOnInit();
    expect(montaje.componente['error']()).toContain('No se pudo conectar');
  });

  it('registra una entrada de mercancía y recarga', () => {
    const c = abrir();
    c['abrirMovimiento']('ENTRADA', arroz);
    expect(c['accion']()).toBe('ENTRADA');
    expect(c['unidadSeleccionada']()).toBe('g');
    expect(navegador.ventana.scrollTo).toHaveBeenCalled();
    c['formMovimiento'].patchValue({ cantidad: 500, motivo: '  Compra semanal ' });
    c['guardarMovimiento']();
    expect(api.entrada).toHaveBeenCalledWith(1, { cantidad: 500, motivo: 'Compra semanal' });
    expect(c['exito']()).toBe('Arroz: ahora hay 5.500 g.');
    expect(c['accion']()).toBeNull();
    expect(api.ingredientes).toHaveBeenCalledTimes(2);
  });

  it('una salida exige el motivo; con motivo la registra', () => {
    const c = abrir();
    c['abrirMovimiento']('SALIDA', arroz);
    c['formMovimiento'].patchValue({ cantidad: 500 });
    c['guardarMovimiento']();
    expect(api.salida).not.toHaveBeenCalled();
    expect(c['invalido']('movimiento', 'motivo')).toBe(true);
    c['formMovimiento'].patchValue({ motivo: 'Merma' });
    c['guardarMovimiento']();
    expect(api.salida).toHaveBeenCalledWith(1, { cantidad: 500, motivo: 'Merma' });
  });

  it('el ajuste envía el stock contado (puede ser 0)', () => {
    const c = abrir();
    c['abrirMovimiento']('AJUSTE', arroz);
    c['formMovimiento'].patchValue({ cantidad: 0 });
    c['guardarMovimiento']();
    expect(api.ajuste).toHaveBeenCalledWith(1, { stockContado: 0, motivo: null });
  });

  it('una entrada o salida en cero se rechaza con un mensaje claro', () => {
    const c = abrir();
    c['abrirMovimiento']('ENTRADA');
    expect(c['unidadSeleccionada']()).toBe('');
    c['formMovimiento'].patchValue({ ingredienteId: 1, cantidad: 0 });
    c['guardarMovimiento']();
    expect(c['error']()).toBe('La cantidad debe ser mayor que cero.');
    expect(api.entrada).not.toHaveBeenCalled();
  });

  it('si el movimiento falla (por ejemplo, salida mayor al stock), muestra el error', () => {
    const c = abrir();
    api.salida.mockReturnValue(falla(409, 'No hay suficiente Arroz'));
    c['abrirMovimiento']('SALIDA', arroz);
    c['formMovimiento'].patchValue({ cantidad: 99999, motivo: 'Merma' });
    c['guardarMovimiento']();
    expect(c['error']()).toBe('No hay suficiente Arroz');
    expect(c['guardando']()).toBe(false);
  });

  it('sin acción abierta, guardar no hace nada', () => {
    const c = abrir();
    c['guardarMovimiento']();
    expect(api.entrada).not.toHaveBeenCalled();
    expect(api.ajuste).not.toHaveBeenCalled();
  });

  it('crea un insumo nuevo validando el formulario', () => {
    const c = abrir();
    c['abrirInsumo']();
    expect(c['esEdicion']()).toBe(false);
    expect(c['nombreInsumo']()).toBe('');
    c['guardarInsumo']();
    expect(api.crear).not.toHaveBeenCalled();
    expect(c['invalido']('insumo', 'nombre')).toBe(true);
    expect(c['invalido']('insumo', 'no-existe')).toBe(false);

    c['formInsumo'].patchValue({ nombre: ' Gaseosa 350 ml ', unidad: 'UNIDAD', stockMinimo: 12, stockInicial: 24 });
    c['guardarInsumo']();
    expect(api.crear).toHaveBeenCalledWith({ nombre: 'Gaseosa 350 ml', unidad: 'UNIDAD', stockMinimo: 12, stockInicial: 24 });
    expect(c['exito']()).toBe('Insumo Gaseosa 350 ml creado.');
    expect(c['editando']()).toBeNull();
  });

  it('edita un insumo existente', () => {
    const c = abrir();
    c['abrirInsumo'](arroz);
    expect(c['esEdicion']()).toBe(true);
    expect(c['nombreInsumo']()).toBe('Arroz');
    c['formInsumo'].patchValue({ nombre: 'Arroz blanco' });
    c['guardarInsumo']();
    expect(api.editar).toHaveBeenCalledWith(1, { nombre: 'Arroz blanco', unidad: 'GRAMO', stockMinimo: 1000, stockInicial: null });
    expect(c['exito']()).toBe('Insumo Arroz blanco actualizado.');
  });

  it('si guardar el insumo falla, muestra el error', () => {
    const c = abrir();
    api.crear.mockReturnValue(falla(400, 'Ya existe un insumo con ese nombre'));
    c['abrirInsumo']();
    c['formInsumo'].patchValue({ nombre: 'Arroz', stockMinimo: 1 });
    c['guardarInsumo']();
    expect(c['error']()).toBe('Ya existe un insumo con ese nombre');
  });

  it('busca el producto por código de barras (Open Food Facts) y llena el formulario', () => {
    const c = abrir();
    c['abrirInsumo']();
    c['codigoBarras'].set('123');
    expect(c['codigoListo']()).toBe(false);
    c['buscarCodigo']();
    expect(api.buscarPorCodigoBarras).not.toHaveBeenCalled();

    c['codigoBarras'].set('7702004-003508');
    c['buscarCodigo']();
    expect(api.buscarPorCodigoBarras).toHaveBeenCalledWith('7702004003508');
    expect(c['productoExterno']()?.marca).toBe('Postobón');
    expect(c['formInsumo'].controls.nombre.value).toBe('Gaseosa 350 ml');
    expect(c['formInsumo'].controls.unidad.value).toBe('UNIDAD');
    expect(c['buscandoCodigo']()).toBe(false);
  });

  it('si el código no aparece, avisa sin bloquear el formulario', () => {
    const c = abrir();
    api.buscarPorCodigoBarras.mockReturnValue(falla(404, 'No se encontró ningún producto con ese código.'));
    c['abrirInsumo']();
    c['codigoBarras'].set('7702004003509');
    c['buscarCodigo']();
    expect(c['avisoCodigo']()).toBe('No se encontró ningún producto con ese código.');
    expect(c['buscandoCodigo']()).toBe(false);
  });

  it('activa y desactiva insumos; si falla, muestra el error', () => {
    const c = abrir();
    c['alternarEstado'](arroz);
    expect(api.cambiarEstado).toHaveBeenCalledWith(1, false);
    expect(c['exito']()).toBe('Arroz desactivado (su historial se conserva).');
    c['alternarEstado'](viejo);
    expect(c['exito']()).toBe('Arroz activado.');
    api.cambiarEstado.mockReturnValue(falla(403));
    c['alternarEstado'](arroz);
    expect(c['error']()).toBe('Tu rol no tiene permiso para esta acción.');
  });

  it('atender una alerta la quita de la lista; si falla, muestra el error', () => {
    const c = abrir();
    c['atender'](alerta({ id: 1 }));
    expect(c['alertas']().map((a) => a.id)).toEqual([2]);
    api.atender.mockReturnValue(falla(500, 'Error'));
    c['atender'](alerta({ id: 2 }));
    expect(c['error']()).toBe('Error');
  });

  it('cancelar cierra el formulario abierto', () => {
    const c = abrir();
    c['abrirMovimiento']('ENTRADA');
    c['cancelar']();
    expect(c['accion']()).toBeNull();
    expect(c['editando']()).toBeNull();
  });

  it('describe cada movimiento: venta con su pedido o motivo y usuario', () => {
    const c = abrir();
    expect(c['detalleMovimiento'](movimiento({ pedidoId: 12 }))).toBe('Venta · pedido #12');
    expect(c['detalleMovimiento'](movimiento({ motivo: 'Compra', usuarioNombre: 'Laura' }))).toBe('Compra · Laura');
    expect(c['detalleMovimiento'](movimiento({ motivo: null, usuarioNombre: 'Laura' }))).toBe('Laura');
  });
});
