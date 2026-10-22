// Pruebas unitarias con Jest · RF-03 · orden de los estados de cocina
import { siguienteEstado } from './pedido';

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
