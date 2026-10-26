import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CocinaService } from '../../core/services/cocina.service';
import { mensajeDeError } from '../../core/services/errores';
import { hora, minutosDesde } from '../../core/util/formato';
import { EstadoCocina, NOMBRES_ESTADO_COCINA, Pedido, siguienteEstado } from '../../core/entities';
import { environment } from '../../../environments/environment';

/**
 * RF-03 y RF-04 · Pantalla de cocina. Muestra las comandas en orden de llegada, agrupadas por estado,
 * y se actualiza sola (cada environment.refrescoCocinaMs) para que los pedidos nuevos aparezcan sin recargar.
 */
@Component({
  selector: 'app-cocina',
  standalone: false,
  templateUrl: './cocina.html',
  styleUrl: './cocina.scss',
})
export class Cocina implements OnInit, OnDestroy {
  private readonly cocinaApi = inject(CocinaService);
  private temporizador?: ReturnType<typeof setInterval>;

  protected readonly hora = hora;
  protected readonly nombresEstado = NOMBRES_ESTADO_COCINA;
  protected readonly columnas: { estado: EstadoCocina; titulo: string }[] = [
    { estado: 'PENDIENTE', titulo: 'Pendientes' },
    { estado: 'EN_PREPARACION', titulo: 'En preparación' },
    { estado: 'LISTO', titulo: 'Listos para entregar' },
  ];
  private readonly textoBoton: Record<EstadoCocina, string> = {
    PENDIENTE: '',
    EN_PREPARACION: 'Iniciar preparación',
    LISTO: 'Marcar listo',
    ENTREGADO: 'Marcar entregado',
  };

  protected readonly comandas = signal<Pedido[]>([]);
  protected readonly ahora = signal(Date.now());
  protected readonly actualizado = signal<Date | null>(null);
  protected readonly procesando = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly total = computed(() => this.comandas().length);

  ngOnInit(): void {
    this.cargar();
    this.temporizador = setInterval(() => this.cargar(), environment.refrescoCocinaMs);
  }

  ngOnDestroy(): void {
    clearInterval(this.temporizador);
  }

  protected deEstado(estado: EstadoCocina): Pedido[] {
    return this.comandas().filter((p) => p.estadoCocina === estado);
  }

  protected minutos(pedido: Pedido): number {
    return minutosDesde(pedido.fechaCreacion, this.ahora());
  }

  /** Texto del botón que lleva el pedido a su siguiente estado. */
  protected accion(pedido: Pedido): string {
    const siguiente = siguienteEstado(pedido.estadoCocina);
    return siguiente ? this.textoBoton[siguiente] : '';
  }

  protected avanzar(pedido: Pedido): void {
    const siguiente = siguienteEstado(pedido.estadoCocina);
    if (!siguiente) return;
    this.procesando.set(pedido.id);
    this.cocinaApi.cambiarEstado(pedido.id, siguiente).subscribe({
      next: () => {
        this.procesando.set(null);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(null);
        this.error.set(mensajeDeError(err));
        this.cargar();
      },
    });
  }

  protected cargar(): void {
    this.cocinaApi.comandas().subscribe({
      next: (lista) => {
        this.comandas.set(lista);
        this.ahora.set(Date.now());
        this.actualizado.set(new Date());
        this.error.set(null);
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }
}
