import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CrearUsuarioRequest, Usuario } from '../models/auth.models';

/** HU-03: habla con /api/usuarios (solo ADMIN): crear, listar, desactivar y reactivar cuentas. */
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/usuarios`;

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.api);
  }

  crear(datos: CrearUsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.api, datos);
  }

  /** Desactiva (false) o reactiva (true) una cuenta. La cuenta no se borra: su historial se conserva. */
  cambiarEstado(id: number, activa: boolean): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.api}/${id}/estado`, { activa });
  }
}
