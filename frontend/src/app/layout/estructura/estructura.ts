import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { environment } from '../../../environments/environment';
import { NOMBRES_ROL, Rol } from '../../core/entities';

type Grupo = 'Operación' | 'Gestión' | 'Análisis';

interface ItemMenu {
  etiqueta: string;
  icono: string;       // trazo SVG del ícono
  grupo: Grupo;        // sección del menú en la que aparece
  ruta?: string;       // sin ruta = módulo de un sprint futuro
  roles?: Rol[];       // sin roles = lo ven todos
  ai?: boolean;
}

/** Estructura común de las páginas internas: menú lateral + contenido. */
@Component({
  selector: 'app-estructura',
  standalone: false,
  templateUrl: './estructura.html',
  styleUrl: './estructura.scss',
})
export class Estructura {
  private auth = inject(AuthService);
  private router = inject(Router);

  protected readonly usuario = this.auth.usuario;
  /** Entorno en el que corre la app (desarrollo, pre-produccion o produccion). */
  protected readonly entorno = environment;
  protected readonly iniciales = (this.usuario?.nombre ?? '?')
    .split(' ').filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  protected readonly nombresRol = NOMBRES_ROL;

  private readonly todos: ItemMenu[] = [
    { etiqueta: 'Dashboard', grupo: 'Operación', ruta: '/inicio', icono: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z' },
    { etiqueta: 'Pedidos', grupo: 'Operación', ruta: '/pedidos', roles: ['MESERO', 'ADMIN'], icono: 'M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6' },
    { etiqueta: 'Cocina', grupo: 'Operación', ruta: '/cocina', roles: ['COCINA', 'ADMIN'], icono: 'M4 14h16v3a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3zM8 10c0-2 2-2 2-4M12 10c0-2 2-2 2-4M16 10c0-2 2-2 2-4' },
    { etiqueta: 'Inventario', grupo: 'Gestión', icono: 'M4 7l8-4 8 4v10l-8 4-8-4zM4 7l8 4 8-4M12 11v10' },
    { etiqueta: 'Productos', grupo: 'Gestión', ruta: '/productos', roles: ['ADMIN'], icono: 'M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z' },
    { etiqueta: 'Usuarios', grupo: 'Gestión', ruta: '/usuarios', roles: ['ADMIN'], icono: 'M9 4.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zM3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M16 4.5a3.5 3.5 0 0 1 0 7M18 14c2 .8 3 3 3 6' },
    { etiqueta: 'Configuración', grupo: 'Gestión', icono: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1' },
    { etiqueta: 'RestoFlow AI', grupo: 'Análisis', ai: true, icono: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z' },
    { etiqueta: 'Reportes', grupo: 'Análisis', icono: 'M3 3v18h18M8 17v-5M13 17V8M18 17v-9' },
  ];

  /** Cada rol ve solo los módulos que puede usar (los "Pronto" los ven todos). */
  protected readonly menu = this.todos.filter((i) => !i.roles || this.auth.tieneRol(...i.roles));

  /** El menú agrupado por secciones, como en la maqueta; las secciones vacías no se muestran. */
  protected readonly grupos = (['Operación', 'Gestión', 'Análisis'] as Grupo[])
    .map((titulo) => ({ titulo, items: this.menu.filter((i) => i.grupo === titulo) }))
    .filter((g) => g.items.length > 0);

  protected salir(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
