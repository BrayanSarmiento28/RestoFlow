// Pruebas unitarias con Jest · RF-08 · gráfica de ventas por hora y comparación con ayer
import { columnasPorHora, variacionPorcentual } from './graficas';

describe('columnasPorHora', () => {
  it('sin ventas no hay gráfica', () => {
    expect(columnasPorHora([], 14)).toEqual([]);
  });

  it('va desde la primera venta hasta la hora actual, rellenando las horas sin ventas con 0', () => {
    const columnas = columnasPorHora(
      [{ hora: 8, total: 50000, pedidos: 2 }, { hora: 11, total: 100000, pedidos: 4 }],
      14,
    );
    expect(columnas.map((c) => c.hora)).toEqual([8, 9, 10, 11, 12, 13, 14]);
    expect(columnas[1]).toEqual({ hora: 9, total: 0, pedidos: 0, alto: 0, actual: false });
    expect(columnas[3].alto).toBe(100);       // la hora con más ventas llena la barra
    expect(columnas[0].alto).toBe(50);
    expect(columnas[6].actual).toBe(true);    // la hora en curso
  });

  it('muestra al menos 6 columnas aunque las ventas sean recientes', () => {
    const columnas = columnasPorHora([{ hora: 12, total: 30000, pedidos: 1 }], 12);
    expect(columnas.map((c) => c.hora)).toEqual([7, 8, 9, 10, 11, 12]);
  });

  it('no inventa horas negativas a la madrugada', () => {
    expect(columnasPorHora([{ hora: 1, total: 10, pedidos: 1 }], 1)[0].hora).toBe(0);
  });
});

describe('variacionPorcentual', () => {
  it('calcula la variación redondeada frente a ayer', () => {
    expect(variacionPorcentual(120000, 100000)).toBe(20);
    expect(variacionPorcentual(66000, 100000)).toBe(-34);
  });

  it('si ayer no hubo ventas no hay con qué comparar (null)', () => {
    expect(variacionPorcentual(50000, 0)).toBeNull();
  });
});
