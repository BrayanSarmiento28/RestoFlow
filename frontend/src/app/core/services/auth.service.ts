import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, MensajeResponse, Rol, Usuario } from '../models/auth.models';

const TOKEN_KEY = 'rf_token';
const USUARIO_KEY = 'rf_usuario';
const EXPIRA_KEY = 'rf_expira';

/** Habla con /api/auth del backend y guarda la sesión en el navegador. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/auth`;

  /** HU-01: inicia sesión. Con "Recordarme" la sesión sobrevive al cerrar el navegador. */
  login(datos: LoginRequest, recordarme: boolean): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.api}/login`, datos)
      .pipe(tap((res) => this.guardarSesion(res, recordarme)));
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
    for (const almacen of [localStorage, sessionStorage]) {
      almacen.removeItem(TOKEN_KEY);
      almacen.removeItem(USUARIO_KEY);
      almacen.removeItem(EXPIRA_KEY);
    }
  }

  get token(): string | null {
    return this.leer(TOKEN_KEY);
  }

  get usuario(): Usuario | null {
    const json = this.leer(USUARIO_KEY);
    return json ? (JSON.parse(json) as Usuario) : null;
  }

  estaAutenticado(): boolean {
    const expira = Number(this.leer(EXPIRA_KEY) ?? 0);
    if (!this.token || Date.now() > expira) {
      this.logout();
      return false;
    }
    return true;
  }

  tieneRol(...roles: Rol[]): boolean {
    const usuario = this.usuario;
    return !!usuario && roles.includes(usuario.rol);
  }

  private guardarSesion(res: LoginResponse, recordarme: boolean): void {
    this.logout();
    const almacen = recordarme ? localStorage : sessionStorage;
    almacen.setItem(TOKEN_KEY, res.token);
    almacen.setItem(USUARIO_KEY, JSON.stringify(res.usuario));
    almacen.setItem(EXPIRA_KEY, String(Date.now() + res.expiraEnSegundos * 1000));
  }

  private leer(clave: string): string | null {
    return localStorage.getItem(clave) ?? sessionStorage.getItem(clave);
  }
}