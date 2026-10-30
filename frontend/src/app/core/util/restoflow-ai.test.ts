// Pruebas unitarias con Jest · RestoFlow AI (Sprint 5) · cómo se muestra el análisis
import type { PronosticoInsumo, Recomendacion, VentaDia } from '../models/restoflow-ai.models';
import {
  capitalizar, columnasPorDia, contarPorPrioridad, direccion, fechaLarga, insumosPorComprar, mensajeAlertas, NOMBRE_DIA,
  principales,
  textoCobertura, textoHora, textoTendencia,
} from './restoflow-ai';

function recomendacion(tipo: Recomendacion['tipo'], prioridad: Recomendacion['prioridad'], titulo: string): Recomendacion {
  return { tipo, prioridad, titulo, detalle: '', ingredienteId: null, productoId: null };
}

function insumo(nombre: string, prioridad: PronosticoInsumo['prioridad']): PronosticoInsumo {
  return {
    ingredienteId: 1, nombre, unidad: 'GRAMO', stockActual: 1000, stockMinimo: 500, consumoDiario: 100,
    consumoSemana: 700, tendenciaPorcentaje: null, diasCobertura: 10, prioridad, cantidadSugerida: 0,
  };
}

describe('textoTendencia y direccion', () => {
  it('muestra el signo y "Sin datos" cuando no hay semana anterior', () => {
    expect(textoTendencia(28)).toBe('+28 %');
    expect(textoTendencia(-14)).toBe('-14 %');
    expect(textoTendencia(0)).toBe('0 %');
    expect(textoTendencia(null)).toBe('Sin datos');
  });

  it('un cambio menor a 5 % es estable', () => {
    expect(direccion(12)).toBe('sube');
    expect(direccion(-30)).toBe('baja');
    expect(direccion(4)).toBe('estable');
    expect(direccion(-4)).toBe('estable');
    expect(direccion(null)).toBe('estable');
  });
});

describe('textoCobertura', () => {
  it('días que alcanza el stock, en singular o plural', () => {
    expect(textoCobertura(1)).toBe('1 día');
    expect(textoCobertura(2.5)).toBe('2,5 días');
    expect(textoCobertura(12)).toBe('12 días');
    expect(textoCobertura(null)).toBe('Sin consumo');
  });
});

describe('textoHora', () => {
  it('hora pico como se dice en Colombia', () => {
    expect(textoHora(9)).toBe('9 a. m.');
    expect(textoHora(12)).toBe('12 m.');
    expect(textoHora(14)).toBe('2 p. m.');
    expect(textoHora(0)).toBe('12 a. m.');
    expect(textoHora(null)).toBe('Sin datos');
  });
});

describe('fechaLarga', () => {
  it('convierte AAAA-MM-DD sin correrse un día por la zona horaria', () => {
    expect(fechaLarga('2026-10-31')).toMatch(/^Sábado,? 31 de octubre$/);   // solo la primera letra en mayúscula
    expect(NOMBRE_DIA.SATURDAY).toBe('sábado');
    expect(capitalizar('')).toBe('');
  });
});

describe('mensajeAlertas', () => {
  it('explica cuántas alertas se crearon y cerraron, en singular o plural', () => {
    expect(mensajeAlertas({ creadas: 1, cerradas: 0, yaAvisados: 0 }))
      .toBe('RestoFlow AI revisó el inventario: 1 alerta nueva y 0 cerradas. Las alertas se ven en Inventario.');
  });

  it('si no hay nuevas porque ya estaban avisadas, lo dice', () => {
    expect(mensajeAlertas({ creadas: 0, cerradas: 1, yaAvisados: 2 })).toContain(
      '0 alertas nuevas y 1 cerrada. 2 insumos que se acaban pronto ya tenían una alerta pendiente');
    expect(mensajeAlertas({ creadas: 0, cerradas: 0, yaAvisados: 1 })).toContain('1 insumo que se acaba pronto');
  });
});

describe('columnasPorDia', () => {
  it('la barra más alta es el día de más ventas (100 %)', () => {
    const dias: VentaDia[] = [
      { dia: 'MONDAY', promedio: 400000 },
      { dia: 'SATURDAY', promedio: 800000 },
      { dia: 'SUNDAY', promedio: 0 },
    ];
    const columnas = columnasPorDia(dias);
    expect(columnas.map((c) => c.etiqueta)).toEqual(['Lun', 'Sáb', 'Dom']);
    expect(columnas.map((c) => c.alto)).toEqual([50, 100, 0]);
    expect(columnas.map((c) => c.fuerte)).toEqual([false, true, false]);
  });

  it('sin ventas todas las barras quedan en 0 y ninguna es la más fuerte', () => {
    const columnas = columnasPorDia([{ dia: 'MONDAY', promedio: 0 }]);
    expect(columnas[0].alto).toBe(0);
    expect(columnas[0].fuerte).toBe(false);
  });
});

describe('recomendaciones', () => {
  const lista = [
    recomendacion('COMPRA', 'ALTA', 'Comprar arroz'),
    recomendacion('COMPRA', 'MEDIA', 'Comprar papa'),
    recomendacion('DEMANDA', 'MEDIA', 'Mañana sábado'),
    recomendacion('INFORMACION', 'BAJA', 'Aprendiendo'),
    recomendacion('PRODUCTO', 'BAJA', 'Crece la limonada'),
  ];

  it('cuenta cuántas hay de cada prioridad', () => {
    expect(contarPorPrioridad(lista)).toEqual({ ALTA: 1, MEDIA: 2, BAJA: 2 });
  });

  it('el Dashboard muestra las 3 primeras que piden una acción', () => {
    expect(principales(lista).map((r) => r.titulo)).toEqual(['Comprar arroz', 'Comprar papa', 'Mañana sábado']);
    expect(principales(lista, 10).some((r) => r.tipo === 'INFORMACION')).toBe(false);
  });

  it('insumos por comprar: los que tienen prioridad', () => {
    expect(insumosPorComprar([insumo('Arroz', 'ALTA'), insumo('Sal', null), insumo('Papa', 'MEDIA')])
      .map((i) => i.nombre)).toEqual(['Arroz', 'Papa']);
  });
});
