import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EstadoCocina, Pedido } from '../entities';
import { StockBajo } from '../models/inventario.models';

/** RF-03 y RF-04 · Pantalla de cocina: /api/cocina. */
@Injectable({ providedIn: 'root' })
export class CocinaService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/cocina`;

  comandas(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${this.api}/comandas`);
  }

  /** Usabilidad · insumos en su mínimo o agotados de los productos indicados. */
  stockBajo(productoIds: number[]): Observable<StockBajo[]> {
    const params = new HttpParams().set('productos', productoIds.join(','));
    return this.http.get<StockBajo[]>(`${this.api}/stock`, { params });
  }

  cambiarEstado(pedidoId: number, estado: EstadoCocina): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.api}/pedidos/${pedidoId}/estado`, { estado });
  }
}
