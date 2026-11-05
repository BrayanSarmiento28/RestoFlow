import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AvisosDelMomento } from '../models/avisos.models';
import { RespuestaIA } from '../models/restoflow-ai.models';

/** RestoFlow AI · avisos de la campana (todos los roles; cada uno recibe solo los suyos). */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  consultar(): Observable<AvisosDelMomento> {
    return this.http.get<AvisosDelMomento>(`${this.api}/avisos`);
  }

  /** Resumen de los avisos escrito por Gemini para este rol (con reglas si Gemini no responde). */
  resumen(): Observable<RespuestaIA> {
    return this.http.get<RespuestaIA>(`${this.api}/avisos/resumen`);
  }
}
