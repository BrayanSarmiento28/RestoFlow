import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CrearUsuarioRequest, Usuario } from '../models/auth.models';

/** HU-03: habla con /api/usuarios (solo ADMIN). */
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/usuarios`;

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.api);
  }

  crear(datos: CrearUsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.api, datos);
  }
}
