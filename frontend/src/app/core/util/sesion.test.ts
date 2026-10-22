// Pruebas unitarias con Jest · RF-01 · sesión en el navegador (con almacenes falsos en memoria y reloj fijo)
import type { Usuario } from '../entities/usuario';
import { AlmacenClaveValor, AlmacenSesion, CLAVES_SESION } from './sesion';

/** Doble de prueba de localStorage / sessionStorage. */
class AlmacenEnMemoria implements AlmacenClaveValor {
  datos = new Map<string, string>();
  getItem(clave: string) { return this.datos.get(clave) ?? null; }
  setItem(clave: string, valor: string) { this.datos.set(clave, valor); }
  removeItem(clave: string) { this.datos.delete(clave); }
}

const mesero: Usuario = { id: 3, nombre: 'Laura', email: 'laura@restoflow.com', rol: 'MESERO', activo: true };

describe('AlmacenSesion', () => {
  let local: AlmacenEnMemoria;
  let temporal: AlmacenEnMemoria;
  let ahora: number;
  let sesion: AlmacenSesion;

  beforeEach(() => {
    local = new AlmacenEnMemoria();
    temporal = new AlmacenEnMemoria();
    ahora = 1_000_000;
    sesion = new AlmacenSesion(local, temporal, () => ahora);
  });

  it('sin "Recordarme" guarda la sesión en el almacén temporal (se borra al cerrar la pestaña)', () => {
    sesion.guardar('token-abc', mesero, 60, false);
    expect(temporal.getItem(CLAVES_SESION.token)).toBe('token-abc');
    expect(local.datos.size).toBe(0);
    expect(sesion.token).toBe('token-abc');
    expect(sesion.usuario).toEqual(mesero);
  });

  it('con "Recordarme" la guarda en el almacén persistente', () => {
    sesion.guardar('token-abc', mesero, 60, true);
    expect(local.getItem(CLAVES_SESION.token)).toBe('token-abc');
    expect(temporal.datos.size).toBe(0);
  });

  it('calcula el vencimiento a partir de los segundos que da el backend', () => {
    sesion.guardar('t', mesero, 480 * 60, false);
    expect(temporal.getItem(CLAVES_SESION.expira)).toBe(String(1_000_000 + 480 * 60 * 1000));
  });

  it('la sesión es vigente hasta que pasa la hora de vencimiento; luego se borra sola', () => {
    sesion.guardar('t', mesero, 60, false);
    ahora += 60_000;
    expect(sesion.vigente()).toBe(true);
    ahora += 1;
    expect(sesion.vigente()).toBe(false);
    expect(sesion.token).toBeNull();
    expect(temporal.datos.size).toBe(0);
  });

  it('sin token no hay sesión', () => {
    expect(sesion.vigente()).toBe(false);
    expect(sesion.usuario).toBeNull();
  });

  it('un nuevo login reemplaza la sesión anterior aunque estuviera en el otro almacén', () => {
    sesion.guardar('viejo', mesero, 60, true);
    sesion.guardar('nuevo', mesero, 60, false);
    expect(local.datos.size).toBe(0);
    expect(sesion.token).toBe('nuevo');
  });

  it('cerrar borra todo de los dos almacenes', () => {
    sesion.guardar('t', mesero, 60, true);
    temporal.setItem(CLAVES_SESION.token, 'otro');
    sesion.cerrar();
    expect(local.datos.size).toBe(0);
    expect(temporal.datos.size).toBe(0);
  });

  it('revisa el rol del usuario', () => {
    sesion.guardar('t', mesero, 60, false);
    expect(sesion.tieneRol('MESERO', 'ADMIN')).toBe(true);
    expect(sesion.tieneRol('ADMIN')).toBe(false);
  });

  it('si los datos guardados están dañados, se toma como sin usuario (no se rompe la aplicación)', () => {
    temporal.setItem(CLAVES_SESION.usuario, '{esto no es json');
    expect(sesion.usuario).toBeNull();
    expect(sesion.tieneRol('MESERO')).toBe(false);
  });
});
