// Pruebas unitarias con Jest · RF-03 · orden de los estados de cocina
import { numeroPedido, siguienteEstado } from './pedido';

describe('siguienteEstado', () => {
  it('avanza un paso a la vez: PENDIENTE → EN_PREPARACION → LISTO → ENTREGADO', () => {
    expect(siguienteEstado('PENDIENTE')).toBe('EN_PREPARACION');
    expect(siguienteEstado('EN_PREPARACION')).toBe('LISTO');
    expect(siguienteEstado('LISTO')).toBe('ENTREGADO');
  });

  it('un pedido entregado ya no tiene siguiente estado', () => {
    expect(siguienteEstado('ENTREGADO')).toBeNull();
  });
});

describe('numeroPedido', () => {
  test('usa el número del día; en pedidos antiguos, el id', () => {
    expect(numeroPedido({ id: 275, numeroDelDia: 3 })).toBe(3);
    expect(numeroPedido({ id: 275, numeroDelDia: null })).toBe(275);
    expect(numeroPedido({ id: 275 })).toBe(275);
  });
});
