// Pruebas unitarias con Jest · mensajes de error del API para el usuario
import { mensajeDeError } from './errores';

describe('mensajeDeError', () => {
  it('sin conexión (status 0) pide revisar que el backend esté encendido', () => {
    expect(mensajeDeError({ status: 0, error: null })).toContain('No se pudo conectar');
  });

  it('403 explica que el rol no tiene permiso', () => {
    expect(mensajeDeError({ status: 403, error: { mensaje: 'Forbidden' } })).toBe('Tu rol no tiene permiso para esta acción.');
  });

  it('errores de validación: une los mensajes de cada campo', () => {
    const err = { status: 400, error: { mensaje: 'Datos inválidos', campos: { nombre: 'El nombre es obligatorio', stockMinimo: 'Debe ser 0 o más' } } };
    expect(mensajeDeError(err)).toBe('El nombre es obligatorio. Debe ser 0 o más');
  });

  it('usa el mensaje del backend (por ejemplo 404 de Open Food Facts o 503)', () => {
    expect(mensajeDeError({ status: 404, error: { mensaje: 'No se encontró ningún producto con el código 4006381333931.' } }))
      .toContain('No se encontró');
    expect(mensajeDeError({ status: 503, error: { mensaje: 'Open Food Facts no está disponible en este momento.' } }))
      .toContain('no está disponible');
  });

  it('si el backend no envía mensaje, usa uno general', () => {
    expect(mensajeDeError({ status: 500, error: null })).toBe('No se pudo completar la solicitud.');
  });
});
