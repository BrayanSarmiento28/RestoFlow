// Pruebas unitarias con Jest · RF-05 a RF-07 · cálculos de la pantalla de inventario
import type { Ingrediente } from '../entities/ingrediente';
import type { MovimientoInventario } from '../entities/movimiento-inventario';
import {
  anchoBarra, cantidadMovimiento, consumoDiarioPorVentas, diasRestantes, estadoInsumo, filasOrdenadas,
  iconoInsumo, resumirNombres,
} from './inventario-calculos';

/** Crea un insumo de prueba; enMinimo y agotado se calculan igual que en el backend. */
function insumo(id: number, nombre: string, stockActual: number, stockMinimo: number, activo = true): Ingrediente {
  return {
    id, nombre, unidad: 'GRAMO', stockActual, stockMinimo, activo,
    enMinimo: stockActual <= stockMinimo, agotado: stockActual === 0,
  };
}

function movimiento(ingredienteId: number, tipo: MovimientoInventario['tipo'], cantidad: number,
                    pedidoId: number | null): MovimientoInventario {
  return {
    id: Math.random(), ingredienteId, ingredienteNombre: 'x', unidad: 'GRAMO', tipo, cantidad,
    motivo: null, usuarioId: null, usuarioNombre: 'Sistema', pedidoId, fecha: '2026-10-20T12:00:00',
  };
}

describe('consumoDiarioPorVentas', () => {
  it('promedia en 7 días solo las salidas por ventas (con pedido)', () => {
    const consumo = consumoDiarioPorVentas([
      movimiento(1, 'SALIDA', 300, 10),
      movimiento(1, 'SALIDA', 400, 11),
      movimiento(1, 'SALIDA', 1000, null),   // merma: no es consumo por ventas
      movimiento(1, 'ENTRADA', 5000, null),  // compra: no cuenta
      movimiento(2, 'SALIDA', 70, 12),
    ]);
    expect(consumo.get(1)).toBe(100);         // 700 g / 7 días
    expect(consumo.get(2)).toBe(10);
    expect(consumo.has(3)).toBe(false);
  });
});

describe('diasRestantes', () => {
  it('divide el stock entre el consumo diario', () => {
    expect(diasRestantes(250, 100)).toBe(2.5);
  });

  it('sin ventas recientes no se puede estimar (null)', () => {
    expect(diasRestantes(250, 0)).toBeNull();
  });
});

describe('estadoInsumo', () => {
  it('agotado tiene prioridad sobre todo lo demás', () => {
    expect(estadoInsumo(insumo(1, 'Huevo', 0, 6), 0)).toBe('agotado');
  });

  it('en o bajo el mínimo → minimo', () => {
    expect(estadoInsumo(insumo(1, 'Arroz', 500, 500), null)).toBe('minimo');
  });

  it('alcanza para 3 días o menos según las ventas → pronto', () => {
    expect(estadoInsumo(insumo(1, 'Arroz', 2000, 500), 3)).toBe('pronto');
    expect(estadoInsumo(insumo(1, 'Arroz', 2000, 500), 3.5)).toBe('ok');
  });

  it('hasta 1,5 veces el mínimo → revisar', () => {
    expect(estadoInsumo(insumo(1, 'Arroz', 750, 500), null)).toBe('revisar');
    expect(estadoInsumo(insumo(1, 'Arroz', 751, 500), null)).toBe('ok');
  });
});

describe('anchoBarra', () => {
  it('el mínimo queda en la tercera parte de la barra', () => {
    expect(anchoBarra(500, 500)).toBeCloseTo(33.33, 1);
  });

  it('nunca pasa de 100 %', () => {
    expect(anchoBarra(9000, 500)).toBe(100);
  });

  it('sin mínimo definido la barra va llena si hay stock', () => {
    expect(anchoBarra(20, 0)).toBe(100);
    expect(anchoBarra(0, 0)).toBe(0);
  });
});

describe('iconoInsumo', () => {
  it('elige la imagen según el tipo de insumo, sin importar mayúsculas ni tildes', () => {
    expect(iconoInsumo('Pechuga de pollo')).toBe('img/insumos/carnes.svg');
    expect(iconoInsumo('ARROZ blanco')).toBe('img/insumos/granos.svg');
    expect(iconoInsumo('Limón')).toBe('img/insumos/frutas.svg');
    expect(iconoInsumo('Limon')).toBe('img/insumos/frutas.svg');
  });

  it('sin coincidencia devuelve null (la pantalla muestra la inicial)', () => {
    expect(iconoInsumo('Servilletas')).toBeNull();
  });
});

describe('filasOrdenadas', () => {
  it('deja fuera los inactivos y ordena: agotado, mínimo, pronto, revisar, ok (y luego por nombre)', () => {
    const filas = filasOrdenadas(
      [
        insumo(1, 'Zanahoria', 5000, 500),
        insumo(2, 'Arroz', 600, 500),
        insumo(3, 'Huevo', 0, 6),
        insumo(4, 'Aceite', 400, 500),
        insumo(5, 'Queso', 900, 300),
        insumo(6, 'Tomate', 100, 50, false),
      ],
      [movimiento(5, 'SALIDA', 2100, 7)],    // queso: 300 g/día → alcanza 3 días
    );
    expect(filas.map((f) => f.insumo.nombre)).toEqual(['Huevo', 'Aceite', 'Queso', 'Arroz', 'Zanahoria']);
    expect(filas.map((f) => f.estado)).toEqual(['agotado', 'minimo', 'pronto', 'revisar', 'ok']);
    expect(filas[2].dias).toBe(3);
  });
});

describe('resumirNombres', () => {
  it('hasta 3 nombres los muestra todos', () => {
    expect(resumirNombres(['Arroz', 'Huevo', 'Queso'])).toBe('Arroz, Huevo, Queso');
  });

  it('con más de 3 resume el resto', () => {
    expect(resumirNombres(['Arroz', 'Huevo', 'Queso', 'Leche', 'Papa'])).toBe('Arroz, Huevo y 3 más');
  });
});

describe('cantidadMovimiento', () => {
  it('muestra el signo según el tipo y la abreviatura de la unidad', () => {
    expect(cantidadMovimiento({ tipo: 'ENTRADA', cantidad: 30, unidad: 'UNIDAD' })).toBe('+30 und');
    expect(cantidadMovimiento({ tipo: 'SALIDA', cantidad: 1500, unidad: 'GRAMO' })).toBe('−1.500 g');
    expect(cantidadMovimiento({ tipo: 'AJUSTE', cantidad: 2.5, unidad: 'LITRO' })).toBe('= 2,5 l');
  });
});
