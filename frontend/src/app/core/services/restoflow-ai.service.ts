import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AnalisisRestoFlowAI, ResultadoAlertas } from '../models/restoflow-ai.models';

/** RF-09 a RF-11 · /api/restoflow-ai (ADMIN e INVENTARIO). */
@Injectable({ providedIn: 'root' })
export class RestoFlowAIService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  /** Análisis calculado al momento con los datos actuales. */
  analisis(): Observable<AnalisisRestoFlowAI> {
    return this.http.get<AnalisisRestoFlowAI>(`${this.api}/restoflow-ai/analisis`);
  }

  /** Lo mismo que hace la tarea programada de cada hora: crear y cerrar alertas anticipadas. */
  generarAlertas(): Observable<ResultadoAlertas> {
    return this.http.post<ResultadoAlertas>(`${this.api}/restoflow-ai/alertas`, {});
  }
}
