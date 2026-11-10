// Pruebas unitarias con Jest · código de barras para buscar un insumo en Open Food Facts
import { codigoCompleto, digitoDeControlValido, limpiarCodigo } from './codigo-barras';

describe('limpiarCodigo', () => {
  it('quita espacios y guiones', () => {
    expect(limpiarCodigo(' 7702 011-012345 ')).toBe('7702011012345');
  });

  it('un valor vacío queda como texto vacío', () => {
    expect(limpiarCodigo(null)).toBe('');
    expect(limpiarCodigo(undefined)).toBe('');
  });
});

describe('codigoCompleto', () => {
  it('acepta EAN-8, UPC-12, EAN-13 y GTIN-14', () => {
    expect(codigoCompleto('96385074')).toBe(true);
    expect(codigoCompleto('036000291452')).toBe(true);
    expect(codigoCompleto('7702011012345')).toBe(true);
    expect(codigoCompleto('17702011012342')).toBe(true);
  });

  it('rechaza letras o largos que no existen', () => {
    expect(codigoCompleto('123')).toBe(false);
    expect(codigoCompleto('770201101234A')).toBe(false);
    expect(codigoCompleto('123456789')).toBe(false);
    expect(codigoCompleto('')).toBe(false);
  });
});

describe('digitoDeControlValido', () => {
  it('reconoce códigos reales bien escritos (algoritmo GS1)', () => {
    expect(digitoDeControlValido('4006381333931')).toBe(true);   // EAN-13
    expect(digitoDeControlValido('96385074')).toBe(true);        // EAN-8
    expect(digitoDeControlValido('036000291452')).toBe(true);    // UPC-A
    expect(digitoDeControlValido('7 702011 012345')).toBe(false);
  });

  it('detecta un número mal escrito', () => {
    expect(digitoDeControlValido('4006381333932')).toBe(false);
  });

  it('un código incompleto nunca es válido', () => {
    expect(digitoDeControlValido('400638')).toBe(false);
  });
});
