import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CocinaService } from '../../core/services/cocina.service';
import { AuthService } from '../../core/services/auth.service';
import { mensajeDeError } from '../../core/services/errores';
import { hora, minutosDesde, numero } from '../../core/util/formato';
import {
  ABREVIATURA_UNIDAD, EstadoCocina, NOMBRES_ESTADO_COCINA, Pedido, numeroPedido, siguienteEstado,
} from '../../core/entities';
import { StockBajo } from '../../core/models/inventario.models';
import { environment } from '../../../environments/environment';

/** El stock de los insumos se consulta de nuevo si cambian los platos de las comandas o cada 30 s. */
const REFRESCO_STOCK_MS = 30_000;

/**
 * RF-03 y RF-04 · Pantalla de cocina. Muestra las comandas en orden de llegada, agrupadas por estado,
 * y se actualiza sola (cada environment.refrescoCocinaMs) para que los pedidos nuevos aparezcan sin recargar.
 * Usabilidad: bajo cada plato avisa si un insumo de su receta está en su mínimo o agotado.
 */
@Component({
  selector: 'app-cocina',
  standalone: false,
  templateUrl: './cocina.html',
  styleUrl: './cocina.scss',
})
export class Cocina implements OnInit, OnDestroy {
  private readonly cocinaApi = inject(CocinaService);
  /** El número del pedido solo lo ve el administrador; la cocina trabaja por mesa. */
  protected readonly verNumero = inject(AuthService).tieneRol('ADMIN');
  protected readonly numeroPedido = numeroPedido;
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
  /** Insumos en su mínimo o agotados de los platos que están en las comandas. */
  protected readonly stock = signal<StockBajo[]>([]);
  private stockConsultado = { productos: '', momento: 0 };

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
    // Con una adición, el tiempo cuenta desde la adición: es lo que la cocina tiene que preparar ahora
    return minutosDesde(pedido.fechaAdicion ?? pedido.fechaCreacion, this.ahora());
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

  /** Avisos de stock de un plato: "Aguacate agotado" o "Aguacate: quedan 2 und". */
  protected stockDe(productoId: number): { texto: string; agotado: boolean }[] {
    return this.stock()
      .filter((s) => s.productoId === productoId)
      .map((s) => ({
        texto: s.agotado
          ? `${s.insumo} agotado`
          : `${s.insumo}: quedan ${numero(s.stockActual)} ${ABREVIATURA_UNIDAD[s.unidad]}`,
        agotado: s.agotado,
      }));
  }

  protected cargar(): void {
    this.cocinaApi.comandas().subscribe({
      next: (lista) => {
        this.comandas.set(lista);
        this.ahora.set(Date.now());
        this.actualizado.set(new Date());
        this.error.set(null);
        this.cargarStock(lista);
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }

  /**
   * Consulta el stock solo si cambiaron los platos o pasaron 30 s, para no duplicar las peticiones de la pantalla
   * (que se actualiza cada pocos segundos). Si falla, la cocina sigue trabajando: el aviso de stock es una ayuda.
   */
  private cargarStock(lista: Pedido[]): void {
    const ids = [...new Set(lista.filter((p) => p.estadoCocina !== 'LISTO')
      .flatMap((p) => p.lineas.map((l) => l.productoId)))].sort((a, b) => a - b);
    const clave = ids.join(',');
    const ahora = Date.now();
    if (clave === this.stockConsultado.productos && ahora - this.stockConsultado.momento < REFRESCO_STOCK_MS) {
      return;
    }
    this.stockConsultado = { productos: clave, momento: ahora };
    if (!ids.length) {
      this.stock.set([]);
      return;
    }
    this.cocinaApi.stockBajo(ids).subscribe({
      next: (avisos) => this.stock.set(avisos),
      error: () => this.stock.set([]),
    });
  }
}
