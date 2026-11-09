import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { UsuarioService } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
import { Rol, Usuario } from '../../core/models/auth.models';

/** Qué cuentas muestra la tabla. Por defecto solo las activas, para que la lista quede limpia. */
export type FiltroCuentas = 'activas' | 'inactivas' | 'todas';

/**
 * HU-03 · El administrador crea las cuentas del personal, les asigna un rol y las desactiva o reactiva.
 * Las cuentas no se eliminan: sus pedidos, ventas y movimientos de inventario deben seguir mostrando quién los hizo.
 */
@Component({
  selector: 'app-usuarios',
  standalone: false,
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
})
export class Usuarios implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly usuariosApi = inject(UsuarioService);
  private readonly auth = inject(AuthService);

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
  /** Cuenta que se va a desactivar o reactivar (abre la ventana de confirmación). */
  protected readonly porCambiarEstado = signal<Usuario | null>(null);
  protected readonly cambiandoEstado = signal(false);

  protected readonly filtro = signal<FiltroCuentas>('activas');
  protected readonly filtros: { valor: FiltroCuentas; etiqueta: string }[] = [
    { valor: 'activas', etiqueta: 'Activas' },
    { valor: 'inactivas', etiqueta: 'Inactivas' },
    { valor: 'todas', etiqueta: 'Todas' },
  ];
  /** Cuántas cuentas hay en cada filtro (el número que se ve en cada botón). */
  protected readonly cuenta = computed<Record<FiltroCuentas, number>>(() => {
    const activas = this.usuarios().filter((u) => u.activo).length;
    return { activas, inactivas: this.usuarios().length - activas, todas: this.usuarios().length };
  });
  protected readonly visibles = computed(() => {
    const f = this.filtro();
    return f === 'todas' ? this.usuarios() : this.usuarios().filter((u) => u.activo === (f === 'activas'));
  });

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

  /** El administrador no puede desactivar su propia cuenta (el backend también lo impide). */
  protected esMiCuenta(u: Usuario): boolean {
    return this.auth.usuario?.id === u.id;
  }

  protected cambiarEstado(u: Usuario): void {
    this.error.set(null);
    this.exito.set(null);
    this.porCambiarEstado.set(u);
  }

  protected confirmarCambioEstado(): void {
    const u = this.porCambiarEstado();
    if (!u || this.cambiandoEstado()) return;
    this.cambiandoEstado.set(true);
    this.usuariosApi.cambiarEstado(u.id, !u.activo).subscribe({
      next: (actualizada) => {
        this.cambiandoEstado.set(false);
        this.porCambiarEstado.set(null);
        this.exito.set(actualizada.activo
          ? `La cuenta de ${actualizada.nombre} se reactivó. Ya puede iniciar sesión.`
          : `La cuenta de ${actualizada.nombre} se desactivó. Ya no puede iniciar sesión.`);
        this.cargarUsuarios();
      },
      error: (err: HttpErrorResponse) => {
        this.cambiandoEstado.set(false);
        this.porCambiarEstado.set(null);
        this.error.set(this.mensajeDeError(err));
      },
    });
  }

  @HostListener('document:keydown.escape')
  protected cerrarVentana(): void {
    if (!this.cambiandoEstado()) this.porCambiarEstado.set(null);
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
