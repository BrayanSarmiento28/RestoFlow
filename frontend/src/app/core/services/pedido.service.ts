import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MetodoPago, Pedido } from '../entities';
import { FiltroPedidos, LineaPedidoRequest, MesaOcupada, PedidoRequest } from '../models/pedido.models';

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

  /** Registra la venta con su método de pago. */
  pagar(id: number, metodoPago: MetodoPago): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.api}/${id}/pagar`, { metodoPago });
  }

  /** Mesas con un pedido abierto: no reciben pedidos nuevos hasta que se cobre o se cancele. */
  mesasOcupadas(): Observable<MesaOcupada[]> {
    return this.http.get<MesaOcupada[]>(`${this.api}/mesas-ocupadas`);
  }

  /** Adición: el cliente pide algo más con el pedido ya en cocina (se suma al mismo pedido y a su cuenta). */
  agregarAdicion(id: number, lineas: LineaPedidoRequest[]): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.api}/${id}/adiciones`, { lineas });
  }

  /** Pasa el pedido a otra mesa libre (por ejemplo, el cliente se cambió de lugar). */
  cambiarMesa(id: number, numeroMesa: number): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.api}/${id}/mesa`, { numeroMesa });
  }
}
