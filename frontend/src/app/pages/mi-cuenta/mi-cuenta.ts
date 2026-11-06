import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ValidationErrors, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';
import { mensajeDeError } from '../../core/services/errores';
import { NOMBRES_ROL } from '../../core/entities';

/** La confirmación debe coincidir con la contraseña nueva. */
function contrasenasIguales(grupo: AbstractControl): ValidationErrors | null {
  const nueva = grupo.get('nueva')?.value;
  const confirmar = grupo.get('confirmar')?.value;
  return nueva && confirmar && nueva !== confirmar ? { noCoinciden: true } : null;
}

/**
 * Mi cuenta (antes "Configuración · Pronto"): cada usuario ve sus datos y cambia su contraseña escribiendo la actual.
 * Es distinto de "Recuperar contraseña" (para quien la olvidó, con un código enviado al correo).
 */
@Component({
  selector: 'app-mi-cuenta',
  standalone: false,
  templateUrl: './mi-cuenta.html',
  styleUrl: './mi-cuenta.scss',
})
export class MiCuenta {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly usuario = this.auth.usuario;
  protected readonly nombreRol = this.usuario ? NOMBRES_ROL[this.usuario.rol] : '';

  protected readonly form = this.fb.nonNullable.group({
    actual: ['', Validators.required],
    nueva: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72),
      Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
    confirmar: ['', Validators.required],
  }, { validators: contrasenasIguales });

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);

  protected cambiar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { actual, nueva } = this.form.getRawValue();
    if (actual === nueva) {
      this.error.set('La nueva contraseña debe ser distinta de la actual.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    this.exito.set(null);
    this.auth.cambiarContrasena(actual, nueva).subscribe({
      next: (r) => {
        this.guardando.set(false);
        this.exito.set(r.mensaje);
        this.form.reset();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(err));
      },
    });
  }

  protected invalido(campo: 'actual' | 'nueva' | 'confirmar'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && control.touched;
  }

  /** La confirmación no coincide (solo se muestra cuando ya escribió en ese campo). */
  protected noCoinciden(): boolean {
    return this.form.hasError('noCoinciden') && this.form.controls.confirmar.touched;
  }
}
