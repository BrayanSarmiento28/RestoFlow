import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Alerta, Ingrediente, MovimientoInventario, RecetaIngrediente, TipoMovimiento } from '../entities';
import {
  AjusteRequest, IngredienteRequest, LineaRecetaRequest, MovimientoRequest,
} from '../models/inventario.models';

/** RF-05 a RF-07 · /api/inventario, /api/alertas y /api/productos/{id}/receta. */
@Injectable({ providedIn: 'root' })
export class InventarioService {
  private http = inject(HttpClient);
  private api = environment.apiUrl;

  ingredientes(soloActivos = false): Observable<Ingrediente[]> {
    const params = new HttpParams().set('soloActivos', soloActivos);
    return this.http.get<Ingrediente[]>(`${this.api}/inventario/ingredientes`, { params });
  }

  crear(datos: IngredienteRequest): Observable<Ingrediente> {
    return this.http.post<Ingrediente>(`${this.api}/inventario/ingredientes`, datos);
  }

  editar(id: number, datos: IngredienteRequest): Observable<Ingrediente> {
    return this.http.put<Ingrediente>(`${this.api}/inventario/ingredientes/${id}`, datos);
  }

  cambiarEstado(id: number, activo: boolean): Observable<Ingrediente> {
    return this.http.patch<Ingrediente>(`${this.api}/inventario/ingredientes/${id}/estado`, { activo });
  }

  entrada(id: number, datos: MovimientoRequest): Observable<Ingrediente> {
    return this.http.post<Ingrediente>(`${this.api}/inventario/ingredientes/${id}/entradas`, datos);
  }

  salida(id: number, datos: MovimientoRequest): Observable<Ingrediente> {
    return this.http.post<Ingrediente>(`${this.api}/inventario/ingredientes/${id}/salidas`, datos);
  }

  ajuste(id: number, datos: AjusteRequest): Observable<Ingrediente> {
    return this.http.post<Ingrediente>(`${this.api}/inventario/ingredientes/${id}/ajustes`, datos);
  }

  /** RF-12 · Filtros opcionales; fechas AAAA-MM-DD. */
  movimientos(filtros: { ingredienteId?: number; tipo?: TipoMovimiento; desde?: string; hasta?: string } = {}):
    Observable<MovimientoInventario[]> {
    let params = new HttpParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') params = params.set(clave, valor);
    });
    return this.http.get<MovimientoInventario[]>(`${this.api}/inventario/movimientos`, { params });
  }

  alertas(soloPendientes = true): Observable<Alerta[]> {
    const params = new HttpParams().set('soloPendientes', soloPendientes);
    return this.http.get<Alerta[]>(`${this.api}/alertas`, { params });
  }

  atender(id: number): Observable<Alerta> {
    return this.http.patch<Alerta>(`${this.api}/alertas/${id}/atender`, {});
  }

  receta(productoId: number): Observable<RecetaIngrediente[]> {
    return this.http.get<RecetaIngrediente[]>(`${this.api}/productos/${productoId}/receta`);
  }

  definirReceta(productoId: number, lineas: LineaRecetaRequest[]): Observable<RecetaIngrediente[]> {
    return this.http.put<RecetaIngrediente[]>(`${this.api}/productos/${productoId}/receta`, { lineas });
  }
}
