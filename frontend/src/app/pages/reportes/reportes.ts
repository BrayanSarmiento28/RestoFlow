import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { PanelService } from '../../core/services/panel.service';
import { InventarioService } from '../../core/services/inventario.service';
import { mensajeDeError } from '../../core/services/errores';
import { fechaHora, fechaIso, numero, pesos } from '../../core/util/formato';
import { ResumenVentas } from '../../core/models/inventario.models';
import {
  ABREVIATURA_UNIDAD, EstadoPedido, Ingrediente, MovimientoInventario, NOMBRES_ESTADO_COCINA, NOMBRES_ESTADO_PEDIDO,
  Pedido, TipoMovimiento,
} from '../../core/entities';

type Pestana = 'ventas' | 'pedidos' | 'movimientos';

/** RF-12 · Historial de ventas, pedidos y movimientos de inventario por rango de fechas (solo ADMIN). */
@Component({
  selector: 'app-reportes',
  standalone: false,
  templateUrl: './reportes.html',
  styleUrl: './reportes.scss',
})
export class Reportes implements OnInit {
  private panelApi = inject(PanelService);
  private inventarioApi = inject(InventarioService);

  protected readonly pesos = pesos;
  protected readonly numero = numero;
  protected readonly fechaHora = fechaHora;
  protected readonly abreviatura = ABREVIATURA_UNIDAD;
  protected readonly nombresCocina = NOMBRES_ESTADO_COCINA;
  protected readonly nombresPedido = NOMBRES_ESTADO_PEDIDO;

  protected readonly pestana = signal<Pestana>('ventas');
  protected readonly desde = signal(fechaIso(this.haceDias(6)));
  protected readonly hasta = signal(fechaIso(new Date()));
  protected readonly estado = signal<EstadoPedido | null>(null);
  protected readonly tipo = signal<TipoMovimiento | null>(null);
  protected readonly insumoId = signal<number | null>(null);

  protected readonly ventas = signal<ResumenVentas | null>(null);
  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly movimientos = signal<MovimientoInventario[]>([]);
  protected readonly insumos = signal<Ingrediente[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Altura de cada barra del gráfico de ventas por día, relativa al mejor día. */
  protected readonly barras = computed(() => {
    const dias = this.ventas()?.porDia ?? [];
    const maximo = Math.max(...dias.map((d) => d.total), 1);
    return dias.map((d) => ({ ...d, alto: (d.total / maximo) * 100, etiqueta: this.diaCorto(d.dia) }));
  });

  protected readonly totalPedidos = computed(() => this.pedidos().reduce((s, p) => s + p.total, 0));

  ngOnInit(): void {
    this.inventarioApi.ingredientes().subscribe({ next: (lista) => this.insumos.set(lista) });
    this.consultar();
  }

  protected cambiarPestana(p: Pestana): void {
    this.pestana.set(p);
    this.consultar();
  }

  protected rangoRapido(dias: number): void {
    this.desde.set(fechaIso(this.haceDias(dias - 1)));
    this.hasta.set(fechaIso(new Date()));
    this.consultar();
  }

  protected consultar(): void {
    const desde = this.desde();
    const hasta = this.hasta();
    if (!desde || !hasta) return;
    this.cargando.set(true);
    this.error.set(null);
    const fin = (err?: HttpErrorResponse) => {
      this.cargando.set(false);
      if (err) this.error.set(mensajeDeError(err));
    };
    switch (this.pestana()) {
      case 'ventas':
        this.panelApi.ventas(desde, hasta).subscribe({ next: (r) => { this.ventas.set(r); fin(); }, error: fin });
        break;
      case 'pedidos':
        this.panelApi.pedidos(desde, hasta, this.estado())
          .subscribe({ next: (l) => { this.pedidos.set(l); fin(); }, error: fin });
        break;
      default:
        this.inventarioApi.movimientos({
          desde, hasta, tipo: this.tipo() ?? undefined, ingredienteId: this.insumoId() ?? undefined,
        }).subscribe({ next: (l) => { this.movimientos.set(l); fin(); }, error: fin });
    }
  }

  protected productos(p: Pedido): string {
    return p.lineas.map((l) => `${l.cantidad}× ${l.productoNombre}`).join(', ');
  }

  protected cantidad(m: MovimientoInventario): string {
    const signo = m.tipo === 'ENTRADA' ? '+' : m.tipo === 'SALIDA' ? '−' : '= ';
    return `${signo}${numero(m.cantidad)} ${this.abreviatura[m.unidad]}`;
  }

  private diaCorto(iso: string): string {
    return new Date(iso + 'T12:00:00').toLocaleDateString('es-CO', { weekday: 'short', day: 'numeric' });
  }

  private haceDias(dias: number): Date {
    const d = new Date();
    d.setDate(d.getDate() - dias);
    return d;
  }
}
