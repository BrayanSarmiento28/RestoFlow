import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EstadoPedido, Pedido } from '../entities';
import { ResumenPanel, ResumenVentas } from '../models/inventario.models';

/** RF-08 y RF-12 · /api/panel y /api/historial (solo ADMIN). */
@Injectable({ providedIn: 'root' })
export class PanelService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  /** Sin fecha: el día de hoy. */
  resumen(fecha?: string): Observable<ResumenPanel> {
    const params = fecha ? new HttpParams().set('fecha', fecha) : new HttpParams();
    return this.http.get<ResumenPanel>(`${this.api}/panel/resumen`, { params });
  }

  ventas(desde: string, hasta: string): Observable<ResumenVentas> {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<ResumenVentas>(`${this.api}/historial/ventas`, { params });
  }

  pedidos(desde: string, hasta: string, estado?: EstadoPedido | null): Observable<Pedido[]> {
    let params = new HttpParams().set('desde', desde).set('hasta', hasta);
    if (estado) params = params.set('estado', estado);
    return this.http.get<Pedido[]>(`${this.api}/historial/pedidos`, { params });
  }
}
