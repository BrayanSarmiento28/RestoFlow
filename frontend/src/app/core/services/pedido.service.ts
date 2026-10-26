import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pedido } from '../entities';
import { FiltroPedidos, PedidoRequest } from '../models/pedido.models';

/** RF-02 · Pedidos del mesero: /api/pedidos. */
@Injectable({ providedIn: 'root' })
export class PedidoService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/pedidos`;

  consultar(filtro: FiltroPedidos = {}): Observable<Pedido[]> {
    let params = new HttpParams();
    if (filtro.mesa) params = params.set('mesa', filtro.mesa);
    if (filtro.estadoCocina) params = params.set('estadoCocina', filtro.estadoCocina);
    if (filtro.estadoPedido) params = params.set('estadoPedido', filtro.estadoPedido);
    return this.http.get<Pedido[]>(this.api, { params });
  }

  registrar(datos: PedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(this.api, datos);
  }

  modificar(id: number, datos: PedidoRequest): Observable<Pedido> {
    return this.http.put<Pedido>(`${this.api}/${id}`, datos);
  }

  cancelar(id: number): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.api}/${id}/cancelar`, {});
  }

  pagar(id: number): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.api}/${id}/pagar`, {});
  }
}
