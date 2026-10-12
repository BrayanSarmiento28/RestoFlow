import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Categoria, Producto } from '../entities';
import { ProductoRequest } from '../models/pedido.models';

/** Menú del restaurante: /api/categorias y /api/productos. */
@Injectable({ providedIn: 'root' })
export class CatalogoService {
  private http = inject(HttpClient);
  private api = environment.apiUrl;

  categorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(`${this.api}/categorias`);
  }

  crearCategoria(nombre: string): Observable<Categoria> {
    return this.http.post<Categoria>(`${this.api}/categorias`, { nombre });
  }

  /** soloActivos = true: lo que el mesero puede vender. */
  productos(soloActivos = false): Observable<Producto[]> {
    const params = new HttpParams().set('soloActivos', soloActivos);
    return this.http.get<Producto[]>(`${this.api}/productos`, { params });
  }

  crearProducto(datos: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>(`${this.api}/productos`, datos);
  }

  editarProducto(id: number, datos: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(`${this.api}/productos/${id}`, datos);
  }

  cambiarDisponibilidad(id: number, activo: boolean): Observable<Producto> {
    return this.http.patch<Producto>(`${this.api}/productos/${id}/disponibilidad`, { activo });
  }
}
