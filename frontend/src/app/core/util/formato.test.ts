// Pruebas unitarias con Jest · core/util/formato.ts (formatos de dinero, números y fechas en español de Colombia)
import { fechaHora, fechaIso, hora, minutosDesde, numero, pesos } from './formato';

describe('pesos', () => {
  it('muestra pesos colombianos con punto de miles y sin decimales', () => {
    expect(pesos(32000)).toBe('$ 32.000');
    expect(pesos(1250000)).toBe('$ 1.250.000');
  });

  it('redondea los centavos', () => {
    expect(pesos(15999.6)).toBe('$ 16.000');
  });

  it('trata null y undefined como cero', () => {
    expect(pesos(null)).toBe('$ 0');
    expect(pesos(undefined)).toBe('$ 0');
  });
});

describe('numero', () => {
  it('usa coma decimal y hasta 2 decimales', () => {
    expect(numero(1250.5)).toBe('1.250,5');
    expect(numero(0.333333)).toBe('0,33');
  });

  it('permite indicar cuántos decimales mostrar', () => {
    expect(numero(2.756, 1)).toBe('2,8');
  });

  it('un valor vacío se muestra como 0', () => {
    expect(numero(null)).toBe('0');
  });
});

describe('fechaIso', () => {
  it('devuelve AAAA-MM-DD con la fecha local (la que espera el API)', () => {
    expect(fechaIso(new Date(2026, 9, 20, 23, 30))).toBe('2026-10-20');
    expect(fechaIso(new Date(2026, 0, 5, 0, 5))).toBe('2026-01-05');
  });
});

describe('minutosDesde', () => {
  const ahora = new Date('2026-10-20T15:00:00Z').getTime();

  it('cuenta los minutos completos transcurridos', () => {
    expect(minutosDesde('2026-10-20T14:47:30Z', ahora)).toBe(12);
  });

  it('nunca devuelve negativo (relojes desfasados) y sin fecha devuelve 0', () => {
    expect(minutosDesde('2026-10-20T15:02:00Z', ahora)).toBe(0);
    expect(minutosDesde(null, ahora)).toBe(0);
  });
});

describe('hora y fechaHora', () => {
  // 19:35 en UTC son las 2:35 de la tarde en Colombia (jest.config.js fija la zona America/Bogota)
  it('muestran la hora de Colombia en formato de 12 horas', () => {
    expect(hora('2026-10-20T19:35:00Z')).toMatch(/^2:35\sp\.\sm\.$/);
    expect(fechaHora('2026-10-15T19:35:00Z')).toMatch(/^15 (de )?oct.*2:35\sp\.\sm\.$/);
  });

  it('sin fecha devuelven texto vacío', () => {
    expect(hora(null)).toBe('');
    expect(fechaHora(undefined)).toBe('');
  });
});
