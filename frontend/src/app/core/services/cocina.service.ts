import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EstadoCocina, Pedido } from '../entities';

/** RF-03 y RF-04 · Pantalla de cocina: /api/cocina. */
@Injectable({ providedIn: 'root' })
export class CocinaService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/cocina`;

  comandas(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${this.api}/comandas`);
  }

  cambiarEstado(pedidoId: number, estado: EstadoCocina): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.api}/pedidos/${pedidoId}/estado`, { estado });
  }
}
