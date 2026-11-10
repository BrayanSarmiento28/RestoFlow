// Pruebas unitarias con Jest · RF-01 · decisiones de los guards de las rutas y del interceptor HTTP
import { debeAdjuntarToken, debeCerrarSesion, decidirAcceso } from './acceso';

describe('decidirAcceso', () => {
  it('sin sesión siempre envía al login', () => {
    expect(decidirAcceso(false, null)).toBe('/login');
    expect(decidirAcceso(false, 'ADMIN', ['ADMIN'])).toBe('/login');
  });

  it('una ruta sin roles solo exige haber iniciado sesión', () => {
    expect(decidirAcceso(true, 'COCINA')).toBe(true);
  });

  it('con sesión y el rol permitido deja pasar', () => {
    expect(decidirAcceso(true, 'COCINA', ['COCINA', 'ADMIN'])).toBe(true);
  });

  it('con sesión pero sin el rol envía al inicio (no al login)', () => {
    expect(decidirAcceso(true, 'MESERO', ['COCINA', 'ADMIN'])).toBe('/inicio');
    expect(decidirAcceso(true, undefined, ['ADMIN'])).toBe('/inicio');
  });
});

describe('debeAdjuntarToken', () => {
  const api = 'http://localhost:8080/api';

  it('agrega el token solo a las peticiones a nuestro API', () => {
    expect(debeAdjuntarToken('http://localhost:8080/api/pedidos', api, 'abc')).toBe(true);
    expect(debeAdjuntarToken('https://images.openfoodfacts.org/arroz.jpg', api, 'abc')).toBe(false);
  });

  it('sin token no agrega nada', () => {
    expect(debeAdjuntarToken('http://localhost:8080/api/pedidos', api, null)).toBe(false);
  });

  it('en Docker el API es relativo (/api) y también funciona', () => {
    expect(debeAdjuntarToken('/api/inventario/ingredientes', '/api', 'abc')).toBe(true);
  });
});

describe('debeCerrarSesion', () => {
  it('un 401 con sesión abierta cierra la sesión (token vencido)', () => {
    expect(debeCerrarSesion(401, true)).toBe(true);
  });

  it('un 401 sin sesión (login fallido) o cualquier otro error no la cierra', () => {
    expect(debeCerrarSesion(401, false)).toBe(false);
    expect(debeCerrarSesion(403, true)).toBe(false);
    expect(debeCerrarSesion(500, true)).toBe(false);
  });
});
