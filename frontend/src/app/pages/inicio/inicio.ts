import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { Rol } from '../../core/entities';

interface Acceso {
  titulo: string;
  texto: string;
  ruta: string;
  roles: Rol[];
}

@Component({
  selector: 'app-inicio',
  standalone: false,
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  private auth = inject(AuthService);
  protected readonly usuario = this.auth.usuario;
  protected readonly hoy = new Date().toLocaleDateString('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  });

  private readonly todos: Acceso[] = [
    { titulo: 'Tomar un pedido', texto: 'Arma el pedido de una mesa y envíalo a cocina.', ruta: '/pedidos', roles: ['MESERO', 'ADMIN'] },
    { titulo: 'Ver comandas', texto: 'Prepara los pedidos en orden de llegada.', ruta: '/cocina', roles: ['COCINA', 'ADMIN'] },
    { titulo: 'Menú y precios', texto: 'Crea productos, cambia precios y desactívalos.', ruta: '/productos', roles: ['ADMIN'] },
    { titulo: 'Cuentas del personal', texto: 'Crea las cuentas de tu equipo y asigna su rol.', ruta: '/usuarios', roles: ['ADMIN'] },
  ];

  /** Accesos directos según el rol de quien inició sesión. */
  protected readonly accesos = this.todos.filter((a) => this.auth.tieneRol(...a.roles));
}
