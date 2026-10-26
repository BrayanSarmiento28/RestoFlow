import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, MensajeResponse, Rol, Usuario } from '../models/auth.models';
import { AlmacenSesion } from '../util/sesion';

/** Habla con /api/auth del backend; la sesión la guarda AlmacenSesion (core/util/sesion.ts) en el navegador. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/auth`;
  private readonly sesion = new AlmacenSesion(localStorage, sessionStorage);

  /** HU-01: inicia sesión. Con "Recordarme" la sesión sobrevive al cerrar el navegador. */
  login(datos: LoginRequest, recordarme: boolean): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.api}/login`, datos)
      .pipe(tap((res) => this.sesion.guardar(res.token, res.usuario, res.expiraEnSegundos, recordarme)));
  }

  /** HU-02 paso 1: pide el código de 6 dígitos. */
  recuperar(email: string): Observable<MensajeResponse> {
    return this.http.post<MensajeResponse>(`${this.api}/recuperar`, { email });
  }

  /** HU-02 paso 2: cambia la contraseña con el código. */
  restablecer(email: string, codigo: string, nuevaPassword: string): Observable<MensajeResponse> {
    return this.http.post<MensajeResponse>(`${this.api}/restablecer`, { email, codigo, nuevaPassword });
  }

  logout(): void {
    this.sesion.cerrar();
  }

  get token(): string | null {
    return this.sesion.token;
  }

  get usuario(): Usuario | null {
    return this.sesion.usuario;
  }

  estaAutenticado(): boolean {
    return this.sesion.vigente();
  }

  tieneRol(...roles: Rol[]): boolean {
    return this.sesion.tieneRol(...roles);
  }
}
