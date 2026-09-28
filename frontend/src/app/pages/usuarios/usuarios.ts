import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { UsuarioService } from '../../core/services/usuario.service';
import { Rol, Usuario } from '../../core/models/auth.models';

/** HU-03 · El administrador crea las cuentas del personal y les asigna un rol. */
@Component({
  selector: 'app-usuarios',
  standalone: false,
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
})
export class Usuarios implements OnInit {
  private fb = inject(FormBuilder);
  private usuariosApi = inject(UsuarioService);

  protected readonly roles: { valor: Rol; etiqueta: string }[] = [
    { valor: 'MESERO', etiqueta: 'Mesero' },
    { valor: 'COCINA', etiqueta: 'Cocina' },
    { valor: 'INVENTARIO', etiqueta: 'Inventario' },
    { valor: 'ADMIN', etiqueta: 'Administrador' },
  ];

  protected readonly usuarios = signal<Usuario[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    // Mismas reglas del backend: mínimo 8 caracteres, al menos una letra y un número
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
    rol: ['MESERO' as Rol, Validators.required],
  });

  ngOnInit(): void {
    this.cargarUsuarios();
  }

  protected crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const datos = this.form.getRawValue();
    this.cargando.set(true);
    this.error.set(null);
    this.exito.set(null);

    this.usuariosApi.crear({ ...datos, nombre: datos.nombre.trim(), email: datos.email.trim() }).subscribe({
      next: (u) => {
        this.cargando.set(false);
        this.exito.set(`Cuenta de ${u.nombre} creada con el rol ${this.nombreRol(u.rol)}.`);
        this.form.reset();
        this.cargarUsuarios();
      },
      error: (err: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.mensajeDeError(err));
      },
    });
  }

  protected invalido(campo: 'nombre' | 'email' | 'password'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && control.touched;
  }

  protected nombreRol(rol: Rol): string {
    return this.roles.find((r) => r.valor === rol)?.etiqueta ?? rol;
  }

  private cargarUsuarios(): void {
    this.usuariosApi.listar().subscribe({
      next: (lista) => this.usuarios.set(lista),
      error: (err: HttpErrorResponse) => this.error.set(this.mensajeDeError(err)),
    });
  }

  private mensajeDeError(err: HttpErrorResponse): string {
    if (err.status === 0) return 'No se pudo conectar con el servidor. Verifica que el backend esté encendido.';
    if (err.status === 403) return 'Solo un administrador puede gestionar cuentas.';
    const campos = err.error?.campos ? Object.values(err.error.campos).join('. ') : '';
    return campos || err.error?.mensaje || 'No se pudo completar la solicitud.';
  }
}
