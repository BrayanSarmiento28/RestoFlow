import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-inicio',
  standalone: false,
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  private auth = inject(AuthService);
  private router = inject(Router);
  protected readonly usuario = this.auth.usuario;

  protected salir(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}