import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

interface ItemMenu {
  etiqueta: string;
  icono: string;       // trazo SVG del ícono
  ruta?: string;       // sin ruta = módulo de un sprint futuro
  soloAdmin?: boolean;
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
  protected readonly iniciales = (this.usuario?.nombre ?? '?')
    .split(' ').filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  protected readonly nombresRol: Record<string, string> = {
    ADMIN: 'Administrador', MESERO: 'Mesero', COCINA: 'Cocina', INVENTARIO: 'Inventario',
  };

  private readonly todos: ItemMenu[] = [
    { etiqueta: 'Dashboard', ruta: '/inicio', icono: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z' },
    { etiqueta: 'Pedidos', icono: 'M9 2h6v4H9zM15 4h3v18H6V4h3M9 12h6M9 16h6' },
    { etiqueta: 'Cocina', icono: 'M6 13.87A4 4 0 0 1 7.41 6a5 5 0 0 1 9.18 0A4 4 0 0 1 18 13.87V21H6zM6 17h12' },
    { etiqueta: 'Inventario', icono: 'M21 8 12 3 3 8v8l9 5 9-5zM3 8l9 5 9-5M12 13v8' },
    { etiqueta: 'Productos', icono: 'M3 2v7a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2V2M6 2v20M21 15V2a5 5 0 0 0-5 5v6a2 2 0 0 0 2 2h3v7' },
    { etiqueta: 'RestoFlow AI', ai: true, icono: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z' },
    { etiqueta: 'Reportes', icono: 'M3 3v18h18M8 17v-5M13 17V8M18 17v-9' },
    { etiqueta: 'Usuarios', ruta: '/usuarios', soloAdmin: true, icono: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75' },
    { etiqueta: 'Configuración', icono: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1' },
  ];

  protected readonly menu = this.todos.filter((i) => !i.soloAdmin || this.auth.tieneRol('ADMIN'));

  protected salir(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}