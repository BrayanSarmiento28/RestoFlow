import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';
import { PanelService } from '../../core/services/panel.service';
import { PedidoService } from '../../core/services/pedido.service';
import { InventarioService } from '../../core/services/inventario.service';
import { mensajeDeError } from '../../core/services/errores';
import { environment } from '../../../environments/environment';
import { numero, pesos } from '../../core/util/formato';
import { columnasPorHora, variacionPorcentual } from '../../core/util/graficas';
import { ResumenPanel } from '../../core/models/inventario.models';
import { ABREVIATURA_UNIDAD, Ingrediente, NOMBRES_ESTADO_COCINA, Pedido, Rol } from '../../core/entities';

interface Acceso {
  titulo: string;
  texto: string;
  ruta: string;
  roles: Rol[];
}

/** RF-08 · Dashboard: resumen del día para el administrador y accesos directos según el rol. */
@Component({
  selector: 'app-inicio',
  standalone: false,
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio implements OnInit, OnDestroy {
  private auth = inject(AuthService);
  private panelApi = inject(PanelService);
  private pedidoApi = inject(PedidoService);
  private inventarioApi = inject(InventarioService);
  private temporizador?: ReturnType<typeof setInterval>;

  protected readonly usuario = this.auth.usuario;
  protected readonly esAdmin = this.auth.tieneRol('ADMIN');
  protected readonly esInventario = this.auth.tieneRol('INVENTARIO');
  protected readonly pesos = pesos;
  protected readonly numero = numero;
  protected readonly abreviatura = ABREVIATURA_UNIDAD;
  protected readonly nombresCocina = NOMBRES_ESTADO_COCINA;
  protected readonly hoy = new Date().toLocaleDateString('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  });

  protected readonly resumen = signal<ResumenPanel | null>(null);
  protected readonly abiertos = signal<Pedido[]>([]);
  protected readonly insumosBajos = signal<Ingrediente[]>([]);
  protected readonly alertasPendientes = signal(0);
  protected readonly error = signal<string | null>(null);

  private readonly todos: Acceso[] = [
    { titulo: 'Tomar un pedido', texto: 'Arma el pedido de una mesa y envíalo a cocina.', ruta: '/pedidos', roles: ['MESERO', 'ADMIN'] },
    { titulo: 'Ver comandas', texto: 'Prepara los pedidos en orden de llegada.', ruta: '/cocina', roles: ['COCINA', 'ADMIN'] },
    { titulo: 'Inventario', texto: 'Registra compras, mermas y conteos; revisa las alertas.', ruta: '/inventario', roles: ['INVENTARIO', 'ADMIN'] },
    { titulo: 'Menú y precios', texto: 'Crea productos, cambia precios y desactívalos.', ruta: '/productos', roles: ['ADMIN'] },
    { titulo: 'Reportes', texto: 'Historial de ventas, pedidos y movimientos de inventario.', ruta: '/reportes', roles: ['ADMIN'] },
    { titulo: 'Cuentas del personal', texto: 'Crea las cuentas de tu equipo y asigna su rol.', ruta: '/usuarios', roles: ['ADMIN'] },
  ];

  /** Accesos directos según el rol de quien inició sesión. */
  protected readonly accesos = this.todos.filter((a) => this.auth.tieneRol(...a.roles));

  /** Ventas por hora, desde la primera hora con ventas hasta la hora actual (cálculo en core/util/graficas.ts). */
  protected readonly horas = computed(() => columnasPorHora(this.resumen()?.ventasPorHora ?? [], new Date().getHours()));
  protected readonly maximoHora = computed(() => Math.max(...this.horas().map((h) => h.total), 0));

  /** Variación de las ventas frente a ayer, en %. */
  protected readonly variacion = computed(() => {
    const r = this.resumen();
    return r ? variacionPorcentual(r.ventas, r.ventasDiaAnterior) : null;
  });

  /** Una mesa por pedido abierto, con el estado de cocina del pedido. */
  protected readonly mesas = computed(() =>
    [...this.abiertos()].sort((a, b) => a.numeroMesa - b.numeroMesa).map((p) => ({
      mesa: p.numeroMesa,
      estado: p.estadoCocina,
      total: p.total,
    })),
  );

  ngOnInit(): void {
    this.cargar();
    if (this.esAdmin) {
      this.temporizador = setInterval(() => this.cargar(), environment.refrescoPedidosMs * 4);
    }
  }

  ngOnDestroy(): void {
    clearInterval(this.temporizador);
  }

  private cargar(): void {
    const fallo = (err: HttpErrorResponse) => this.error.set(mensajeDeError(err));
    if (this.esAdmin) {
      this.panelApi.resumen().subscribe({ next: (r) => this.resumen.set(r), error: fallo });
      this.pedidoApi.consultar({ estadoPedido: 'ABIERTO' }).subscribe({ next: (l) => this.abiertos.set(l), error: fallo });
    } else if (this.esInventario) {
      this.inventarioApi.ingredientes(true).subscribe({
        next: (l) => this.insumosBajos.set(l.filter((i) => i.enMinimo)), error: fallo,
      });
      this.inventarioApi.alertas(true).subscribe({ next: (l) => this.alertasPendientes.set(l.length), error: fallo });
    }
  }
}
