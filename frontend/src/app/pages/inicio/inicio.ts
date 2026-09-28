import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-inicio',
  standalone: false,
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  private auth = inject(AuthService);
  protected readonly usuario = this.auth.usuario;
  protected readonly esAdmin = this.auth.tieneRol('ADMIN');
  protected readonly hoy = new Date().toLocaleDateString('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  });
}
