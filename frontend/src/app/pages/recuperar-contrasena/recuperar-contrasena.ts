import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ValidationErrors, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';

/** La nueva contraseña y su confirmación deben ser iguales. */
function contrasenasIguales(grupo: AbstractControl): ValidationErrors | null {
  const nueva = grupo.get('nuevaPassword')?.value;
  const confirmar = grupo.get('confirmar')?.value;
  return nueva && confirmar && nueva !== confirmar ? { noCoinciden: true } : null;
}

/** HU-02 · Recuperar contraseña en 2 pasos: pedir el código y luego cambiar la contraseña. */
@Component({
  selector: 'app-recuperar-contrasena',
  standalone: false,
  templateUrl: './recuperar-contrasena.html',
  styleUrl: './recuperar-contrasena.scss',
})
export class RecuperarContrasena {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  protected readonly paso = signal<1 | 2 | 3>(1);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);

  protected readonly formCorreo = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected readonly formCodigo = this.fb.nonNullable.group(
    {
      codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
      // Mismas reglas del backend: mínimo 8 caracteres, al menos una letra y un número
      nuevaPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
      confirmar: ['', Validators.required],
    },
    { validators: contrasenasIguales },
  );

  get correo(): string {
    return this.formCorreo.controls.email.value.trim();
  }

  /** Paso 1: el backend envía un código de 6 dígitos (vence en 15 minutos). */
  protected enviarCodigo(): void {
    if (this.formCorreo.invalid) {
      this.formCorreo.markAllAsTouched();
      return;
    }
    this.iniciar();
    this.auth.recuperar(this.correo).subscribe({
      next: (res) => {
        this.cargando.set(false);
        this.mensaje.set(res.mensaje);
        this.paso.set(2);
      },
      error: (err) => this.mostrarError(err),
    });
  }

  /** Paso 2: cambia la contraseña con el código recibido. */
  protected restablecer(): void {
    if (this.formCodigo.invalid) {
      this.formCodigo.markAllAsTouched();
      return;
    }
    const { codigo, nuevaPassword } = this.formCodigo.getRawValue();
    this.iniciar();
    this.auth.restablecer(this.correo, codigo, nuevaPassword).subscribe({
      next: (res) => {
        this.cargando.set(false);
        this.mensaje.set(res.mensaje);
        this.paso.set(3);
      },
      error: (err) => this.mostrarError(err),
    });
  }

  protected cambiarCorreo(): void {
    this.formCodigo.reset();
    this.error.set(null);
    this.mensaje.set(null);
    this.paso.set(1);
  }

  protected invalido(control: AbstractControl): boolean {
    return control.invalid && control.touched;
  }

  private iniciar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.mensaje.set(null);
  }

  private mostrarError(err: HttpErrorResponse): void {
    this.cargando.set(false);
    this.error.set(
      err.status === 0
        ? 'No se pudo conectar con el servidor. Verifica que el backend esté encendido.'
        : (err.error?.mensaje ?? 'No se pudo completar la solicitud. Intenta de nuevo.'),
    );
  }
}