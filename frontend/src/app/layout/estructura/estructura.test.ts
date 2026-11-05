// Pruebas con Jest · menú lateral: cada rol ve solo sus módulos; salir cierra la sesión.
import { Router } from '@angular/router';
import { Estructura } from './estructura';
import { AuthService } from '../../core/services/auth.service';
import { Rol } from '../../core/entities';
import { crear, sesionFalsa } from '../../../testing/angular';

function abrir(rol: Rol | null, nombre?: string) {
  const auth = sesionFalsa(rol, nombre);
  const router = { navigate: jest.fn() };
  const montaje = crear(Estructura, [{ provide: AuthService, useValue: auth }, { provide: Router, useValue: router }]);
  return { c: montaje.componente, auth, router, montaje };
}

describe('Menú lateral', () => {
  it('el administrador ve todos los módulos agrupados por sección', () => {
    const { c, montaje } = abrir('ADMIN', 'laura gómez');
    expect(c['grupos'].map((g) => g.titulo)).toEqual(['Operación', 'Gestión', 'Análisis']);
    expect(c['menu']).toHaveLength(9);
    expect(c['iniciales']).toBe('LG');
    montaje.destruir();
  });

  it('el mesero ve solo lo suyo y los módulos que llegarán pronto', () => {
    const { c, montaje } = abrir('MESERO');
    expect(c['menu'].map((i) => i.etiqueta)).toEqual(['Dashboard', 'Pedidos', 'Configuración']);
    expect(c['grupos'].map((g) => g.titulo)).toEqual(['Operación', 'Gestión']);
    montaje.destruir();
  });

  it('sin usuario muestra "?" como iniciales', () => {
    const { c, montaje } = abrir(null);
    expect(c['iniciales']).toBe('?');
    montaje.destruir();
  });

  it('salir cierra la sesión y vuelve al login', () => {
    const { c, auth, router, montaje } = abrir('COCINA');
    c['salir']();
    expect(auth.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    montaje.destruir();
  });
});
