// Pruebas con Jest · pantalla de Pedidos (mesero y administrador) con servicios falsos, sin navegador ni backend.
import { Pedidos } from './pedidos';
import { CatalogoService } from '../../core/services/catalogo.service';
import { PedidoService } from '../../core/services/pedido.service';
import { Rol } from '../../core/entities';
import { MesaOcupada } from '../../core/models/pedido.models';
import { Montaje, conSesion, crear, falla, ok, simularNavegador } from '../../../testing/angular';
import { pedido, producto } from '../../../testing/datos';

const plato = producto({ id: 1, nombre: 'Bandeja paisa', precio: 32000 });
const jugo = producto({ id: 2, nombre: 'Jugo natural', precio: 7000, categoriaNombre: 'Bebidas' });

function apiPedidos(ocupadas: MesaOcupada[] = []) {
  return {
    consultar: jest.fn(() => ok([pedido()])),
    mesasOcupadas: jest.fn(() => ok(ocupadas)),
    registrar: jest.fn(() => ok(pedido({ id: 41, numeroDelDia: 5, numeroMesa: 8 }))),
    modificar: jest.fn(() => ok(pedido())),
    cancelar: jest.fn(() => ok(pedido({ estadoPedido: 'CANCELADO' }))),
    pagar: jest.fn(() => ok(pedido({ estadoPedido: 'PAGADO' }))),
    cambiarMesa: jest.fn(() => ok(pedido({ numeroMesa: 9 }))),
  };
}

describe('Pantalla de Pedidos', () => {
  let montaje: Montaje<Pedidos>;
  let api: ReturnType<typeof apiPedidos>;
  let catalogo: { productos: jest.Mock };
  let navegador: ReturnType<typeof simularNavegador>;

  function abrir(rol: Rol = 'MESERO', ocupadas: MesaOcupada[] = [], respuestas = {}) {
    navegador = simularNavegador(respuestas);
    api = apiPedidos(ocupadas);
    catalogo = { productos: jest.fn(() => ok([plato, jugo])) };
    montaje = crear(Pedidos, [
      { provide: PedidoService, useValue: api },
      { provide: CatalogoService, useValue: catalogo },
      conSesion(rol),
    ]);
    montaje.componente.ngOnInit();
    return montaje.componente;
  }

  afterEach(() => {
    montaje.componente.ngOnDestroy();
    montaje.destruir();
    navegador.restaurar();
  });

  it('al abrir carga el menú disponible, los pedidos activos y las mesas ocupadas', () => {
    const c = abrir();
    expect(catalogo.productos).toHaveBeenCalledWith(true);
    expect(c['productos']()).toHaveLength(2);
    expect(c['pedidos']()).toHaveLength(1);
    expect(api.consultar).toHaveBeenCalledWith({ mesa: null, estadoPedido: 'ABIERTO' });
    expect(api.mesasOcupadas).toHaveBeenCalled();
    expect(c['categorias']()).toEqual(['Todas', 'Platos fuertes', 'Bebidas']);
  });

  it('si el menú no carga, muestra el error del API', () => {
    navegador = simularNavegador();
    api = apiPedidos();
    montaje = crear(Pedidos, [
      { provide: PedidoService, useValue: { ...api, consultar: jest.fn(() => falla(0)) } },
      { provide: CatalogoService, useValue: { productos: () => falla(500, 'Error del menú') } },
      conSesion('MESERO'),
    ]);
    montaje.componente.ngOnInit();
    expect(montaje.componente['error']()).toContain('No se pudo conectar');
  });

  it('el mesero no ve el número del pedido; el administrador sí', () => {
    expect(abrir('MESERO')['verNumero']).toBe(false);
    montaje.componente.ngOnDestroy();
    montaje.destruir();
    navegador.restaurar();
    expect(abrir('ADMIN')['verNumero']).toBe(true);
  });

  it('arma el carrito: agrega, suma, cambia la nota, quita y calcula el total', () => {
    const c = abrir();
    c['agregar'](plato);
    c['agregar'](plato);
    c['agregar'](jugo);
    expect(c['total']()).toBe(71000);
    const [primero, segundo] = c['carrito']();
    c['cambiarCantidad'](primero, -1);
    c['cambiarNota'](segundo, 'Sin hielo');
    expect(c['carrito']()[1].nota).toBe('Sin hielo');
    c['quitar'](c['carrito']()[1]);
    expect(c['carrito']()).toHaveLength(1);
    expect(c['total']()).toBe(32000);
  });

  it('filtra el menú por categoría y por texto', () => {
    const c = abrir();
    c['categoriaActiva'].set('Bebidas');
    expect(c['productosVisibles']().map((p) => p.nombre)).toEqual(['Jugo natural']);
    c['categoriaActiva'].set('Todas');
    c['busqueda'].set('bandeja');
    expect(c['productosVisibles']().map((p) => p.nombre)).toEqual(['Bandeja paisa']);
  });

  it('no envía si falta la mesa, si el carrito está vacío o si la mesa está ocupada', () => {
    const ocupada: MesaOcupada = { numeroMesa: 5, pedidoId: 30, meseroNombre: 'Pedro', estadoCocina: 'LISTO' };
    const c = abrir('MESERO', [ocupada]);
    c['enviar']();
    expect(c['invalido']('mesa')).toBe(true);
    expect(api.registrar).not.toHaveBeenCalled();

    c['form'].controls.mesa.setValue(8);
    c['enviar']();
    expect(c['error']()).toBe('Agrega al menos un producto al pedido.');

    c['agregar'](plato);
    c['form'].controls.mesa.setValue(5);
    expect(c['avisoMesa']()).toContain('La mesa 5 está ocupada');
    c['enviar']();
    expect(c['error']()).toContain('Pedro');
    expect(api.registrar).not.toHaveBeenCalled();
  });

  it('registra el pedido nuevo, limpia el formulario y avisa que se envió a cocina', () => {
    const c = abrir();
    c['agregar'](plato);
    c['form'].controls.mesa.setValue(8);
    c['form'].controls.observaciones.setValue('  Sin cebolla ');
    c['enviar']();
    expect(api.registrar).toHaveBeenCalledWith({
      numeroMesa: 8, observaciones: 'Sin cebolla', lineas: [{ productoId: 1, cantidad: 1, nota: null }],
    });
    expect(c['exito']()).toBe('Pedido de la mesa 8 enviado a cocina.');
    expect(c['carrito']()).toHaveLength(0);
    expect(c['form'].controls.mesa.value).toBeNull();
    expect(c['enviando']()).toBe(false);
  });

  it('si el API rechaza el pedido, muestra su mensaje y conserva el carrito', () => {
    const c = abrir();
    api.registrar.mockReturnValue(falla(409, 'La mesa 8 está ocupada'));
    c['agregar'](plato);
    c['form'].controls.mesa.setValue(8);
    c['enviar']();
    expect(c['error']()).toBe('La mesa 8 está ocupada');
    expect(c['carrito']()).toHaveLength(1);
    expect(c['enviando']()).toBe(false);
  });

  it('edita un pedido pendiente: lo carga, bloquea la mesa y guarda los cambios', () => {
    const c = abrir('ADMIN');
    const p = pedido({ observaciones: 'Rápido' });
    c['editar'](p);
    expect(c['editandoId']()).toBe(40);
    expect(c['editandoNumero']()).toBe(4);
    expect(c['form'].controls.mesa.disabled).toBe(true);
    expect(c['avisoMesa']()).toBeNull();
    expect(navegador.ventana.scrollTo).toHaveBeenCalled();
    c['enviar']();
    expect(api.modificar).toHaveBeenCalledWith(40, {
      numeroMesa: 3, observaciones: 'Rápido', lineas: [{ productoId: 1, cantidad: 2, nota: null }],
    });
    expect(c['exito']()).toBe('Pedido #4 de la mesa 3 actualizado.');
    expect(c['editandoId']()).toBeNull();
    expect(c['form'].controls.mesa.enabled).toBe(true);
  });

  it('no deja editar un pedido con un producto que ya no está en el menú', () => {
    const c = abrir();
    c['editar'](pedido({ lineas: [{ id: 1, productoId: 99, productoNombre: 'Sancocho', cantidad: 1,
      precioUnitario: 1, subtotal: 1, nota: null }] }));
    expect(c['error']()).toContain('Sancocho ya no está disponible');
    expect(c['editandoId']()).toBeNull();
  });

  it('cancelar la edición limpia todo', () => {
    const c = abrir();
    c['editar'](pedido());
    c['cancelarEdicion']();
    expect(c['carrito']()).toHaveLength(0);
    expect(c['editandoId']()).toBeNull();
  });

  it('cancela un pedido solo si el usuario lo confirma', () => {
    let c = abrir('MESERO', [], { confirmar: false });
    c['cancelar'](pedido());
    expect(api.cancelar).not.toHaveBeenCalled();
    montaje.componente.ngOnDestroy();
    montaje.destruir();
    navegador.restaurar();

    c = abrir('MESERO', [], { confirmar: true });
    c['cancelar'](pedido());
    expect(navegador.confirmar).toHaveBeenCalledWith('¿Cancelar el pedido de la mesa 3?');
    expect(api.cancelar).toHaveBeenCalledWith(40);
    expect(c['exito']()).toBe('Pedido de la mesa 3 cancelado.');
  });

  it('si cancelar falla, muestra el error', () => {
    const c = abrir();
    api.cancelar.mockReturnValue(falla(409, 'Ya está en preparación'));
    c['cancelar'](pedido());
    expect(c['error']()).toBe('Ya está en preparación');
  });

  it('cambia el pedido de mesa con la mesa que escribe el mesero', () => {
    const c = abrir('MESERO', [], { preguntar: ' 9 ' });
    c['cambiarMesa'](pedido());
    expect(api.cambiarMesa).toHaveBeenCalledWith(40, 9);
    expect(c['exito']()).toBe('Pedido de la mesa 3 pasado a la mesa 9.');
  });

  it('cambiar de mesa: si cierra el cuadro no hace nada; si la mesa no sirve, lo explica', () => {
    let c = abrir('MESERO', [], { preguntar: null });
    c['cambiarMesa'](pedido());
    expect(api.cambiarMesa).not.toHaveBeenCalled();
    montaje.componente.ngOnDestroy();
    montaje.destruir();
    navegador.restaurar();

    c = abrir('MESERO', [], { preguntar: '3' });
    c['cambiarMesa'](pedido());
    expect(api.cambiarMesa).not.toHaveBeenCalled();
    expect(c['error']()).toBeTruthy();
    expect(c['exito']()).toBeNull();
  });

  it('el administrador registra la venta con el método de pago elegido', () => {
    const c = abrir('ADMIN');
    const p = pedido({ estadoCocina: 'ENTREGADO' });
    c['pagar'](p);
    expect(c['cobrando']()).toBe(p);
    c['confirmarCobro']();
    expect(api.pagar).not.toHaveBeenCalled();

    c['metodoElegido'].set('TRANSFERENCIA');
    c['confirmarCobro']();
    expect(api.pagar).toHaveBeenCalledWith(40, 'TRANSFERENCIA');
    expect(c['cobrando']()).toBeNull();
    expect(c['exito']()).toBe('Venta del pedido #4 de la mesa 3 registrada (transferencia). La mesa quedó libre.');
  });

  it('el mesero solo pasa la cuenta: puede imprimirla pero no registrar el cobro', () => {
    const c = abrir('MESERO');
    c['pagar'](pedido({ estadoCocina: 'ENTREGADO' }));
    c['metodoElegido'].set('EFECTIVO');
    c['confirmarCobro']();
    expect(api.pagar).not.toHaveBeenCalled();
    c['imprimirCuenta']();
    expect(navegador.ventana.print).toHaveBeenCalled();
    expect(c['fechaCuenta']()).toMatch(/2026|\d{4}/);
    c['cerrarCobro']();
    expect(c['cobrando']()).toBeNull();
  });

  it('si el cobro falla, cierra la ventana y muestra el error', () => {
    const c = abrir('ADMIN');
    api.pagar.mockReturnValue(falla(403));
    c['pagar'](pedido());
    c['metodoElegido'].set('EFECTIVO');
    c['confirmarCobro']();
    expect(c['error']()).toBe('Tu rol no tiene permiso para esta acción.');
    expect(c['cobrando']()).toBeNull();
    expect(c['cobrandoEnvio']()).toBe(false);
  });

  it('mientras se envía el cobro, Escape no cierra la ventana', () => {
    const c = abrir('ADMIN');
    c['pagar'](pedido());
    c['cobrandoEnvio'].set(true);
    c['cerrarCobro']();
    expect(c['cobrando']()).not.toBeNull();
    c['confirmarCobro']();
    expect(api.pagar).not.toHaveBeenCalled();
  });

  it('abre y cierra el detalle de un pedido con clic o con Enter y Espacio', () => {
    const c = abrir();
    const tecla = (key: string) => ({ key, preventDefault: jest.fn() }) as unknown as KeyboardEvent;
    c['alternarDetalle'](40);
    expect(c['abierto']()).toBe(40);
    c['teclaEnFila'](tecla('Enter'), 40);
    expect(c['abierto']()).toBeNull();
    c['teclaEnFila'](tecla(' '), 40);
    expect(c['abierto']()).toBe(40);
    c['teclaEnFila'](tecla('a'), 40);
    expect(c['abierto']()).toBe(40);
  });

  it('los filtros consultan de nuevo con el estado y la mesa elegidos', () => {
    const c = abrir();
    c['cambiarFiltroEstado']('LISTO');
    expect(api.consultar).toHaveBeenLastCalledWith({ mesa: null, estadoCocina: 'LISTO' });
    c['cambiarFiltroMesa']('5');
    expect(c['filtroMesa']()).toBe(5);
    expect(api.consultar).toHaveBeenLastCalledWith({ mesa: 5, estadoCocina: 'LISTO' });
    c['cambiarFiltroMesa']('');
    expect(c['filtroMesa']()).toBeNull();
  });

  it('si la lista de pedidos falla, muestra el error', () => {
    const c = abrir();
    api.consultar.mockReturnValue(falla(500, 'Servidor caído'));
    c['cargarPedidos']();
    expect(c['error']()).toBe('Servidor caído');
  });

  it('se actualiza sola cada pocos segundos mientras la pantalla está abierta', () => {
    jest.useFakeTimers();
    try {
      const c = abrir();
      const antes = api.consultar.mock.calls.length;
      jest.advanceTimersByTime(2600);
      expect(api.consultar.mock.calls.length).toBeGreaterThan(antes);
      c.ngOnDestroy();
    } finally {
      jest.useRealTimers();
    }
  });
});

