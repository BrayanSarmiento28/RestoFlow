import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CatalogoService } from '../../core/services/catalogo.service';
import { PedidoService } from '../../core/services/pedido.service';
import { mensajeDeError } from '../../core/services/errores';
import { hora, pesos } from '../../core/util/formato';
import {
  NOMBRES_ESTADO_COCINA, NOMBRES_ESTADO_PEDIDO, Pedido, Producto,
} from '../../core/entities';
import { environment } from '../../../environments/environment';
import {
  FiltroEstado, ItemCarrito, aPedidoRequest, agregarProducto, cambiarCantidad, cambiarNota, carritoDesdePedido,
  categoriasDe, filtrarProductos, filtroDePedidos, quitarItem, totalCarrito,
} from '../../core/util/carrito';

/**
 * RF-02 · Pantalla del mesero: arma el pedido de una mesa, lo envía a cocina
 * y sigue el estado de sus pedidos (se actualiza sola).
 */
@Component({
  selector: 'app-pedidos',
  standalone: false,
  templateUrl: './pedidos.html',
  styleUrl: './pedidos.scss',
})
export class Pedidos implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private catalogoApi = inject(CatalogoService);
  private pedidosApi = inject(PedidoService);
  private temporizador?: ReturnType<typeof setInterval>;

  protected readonly pesos = pesos;
  protected readonly hora = hora;
  protected readonly nombresEstado = NOMBRES_ESTADO_COCINA;
  protected readonly nombresEstadoPedido = NOMBRES_ESTADO_PEDIDO;
  protected readonly filtrosEstado: { valor: FiltroEstado; etiqueta: string }[] = [
    { valor: 'ACTIVOS', etiqueta: 'Activos' },
    { valor: 'PENDIENTE', etiqueta: 'Pendientes' },
    { valor: 'EN_PREPARACION', etiqueta: 'En preparación' },
    { valor: 'LISTO', etiqueta: 'Listos' },
    { valor: 'ENTREGADO', etiqueta: 'Entregados' },
    { valor: 'PAGADO', etiqueta: 'Pagados' },
    { valor: 'CANCELADO', etiqueta: 'Cancelados' },
    { valor: 'TODOS', etiqueta: 'Todos' },
  ];

  // ---------- Menú ----------
  protected readonly productos = signal<Producto[]>([]);
  protected readonly categoriaActiva = signal<string>('Todas');
  protected readonly busqueda = signal('');
  // Lógica del carrito y de los filtros en core/util/carrito.ts (probada con Jest)
  protected readonly categorias = computed(() => categoriasDe(this.productos()));
  protected readonly productosVisibles = computed(() =>
    filtrarProductos(this.productos(), this.categoriaActiva(), this.busqueda()));

  // ---------- Pedido en construcción ----------
  protected readonly carrito = signal<ItemCarrito[]>([]);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly total = computed(() => totalCarrito(this.carrito()));
  protected readonly form = this.fb.group({
    mesa: [null as number | null, [Validators.required, Validators.min(1), Validators.max(999)]],
    observaciones: ['', Validators.maxLength(300)],
  });

  // ---------- Lista de pedidos ----------
  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly filtroEstado = signal<FiltroEstado>('ACTIVOS');
  protected readonly filtroMesa = signal<number | null>(null);
  protected readonly abierto = signal<number | null>(null);

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);

  ngOnInit(): void {
    this.catalogoApi.productos(true).subscribe({
      next: (lista) => this.productos.set(lista),
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
    this.cargarPedidos();
    // Los estados los cambia la cocina: se consultan de nuevo cada cierto tiempo (según el entorno).
    this.temporizador = setInterval(() => this.cargarPedidos(), environment.refrescoPedidosMs);
  }

  ngOnDestroy(): void {
    clearInterval(this.temporizador);
  }

  // ---------- Armar el pedido ----------

  protected agregar(producto: Producto): void {
    this.carrito.update((items) => agregarProducto(items, producto));
  }

  protected cambiarCantidad(item: ItemCarrito, cambio: number): void {
    this.carrito.update((items) => cambiarCantidad(items, item, cambio));
  }

  protected cambiarNota(item: ItemCarrito, nota: string): void {
    this.carrito.update((items) => cambiarNota(items, item, nota));
  }

  protected quitar(item: ItemCarrito): void {
    this.carrito.update((items) => quitarItem(items, item));
  }

  protected limpiar(): void {
    this.carrito.set([]);
    this.editandoId.set(null);
    this.form.reset({ mesa: null, observaciones: '' });
    this.form.controls.mesa.enable();
  }

  /** Registra el pedido nuevo o guarda los cambios del que se está editando. */
  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.carrito().length === 0) {
      this.error.set('Agrega al menos un producto al pedido.');
      return;
    }
    const valores = this.form.getRawValue();
    const datos = aPedidoRequest(valores.mesa, valores.observaciones, this.carrito());
    const id = this.editandoId();
    const peticion = id ? this.pedidosApi.modificar(id, datos) : this.pedidosApi.registrar(datos);

    this.enviando.set(true);
    this.error.set(null);
    this.exito.set(null);
    peticion.subscribe({
      next: (pedido) => {
        this.enviando.set(false);
        this.exito.set(id
          ? `Pedido #${pedido.id} de la mesa ${pedido.numeroMesa} actualizado.`
          : `Pedido #${pedido.id} de la mesa ${pedido.numeroMesa} enviado a cocina.`);
        this.limpiar();
        this.cargarPedidos();
      },
      error: (err: HttpErrorResponse) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(err));
      },
    });
  }

  // ---------- Acciones sobre pedidos existentes ----------

  /** Carga un pedido Pendiente en el formulario para cambiarlo. */
  protected editar(pedido: Pedido): void {
    const { items, faltante } = carritoDesdePedido(pedido, this.productos());
    if (faltante) {
      this.error.set(`El producto ${faltante} ya no está disponible; registra un pedido nuevo.`);
      return;
    }
    this.carrito.set(items);
    this.editandoId.set(pedido.id);
    this.form.reset({ mesa: pedido.numeroMesa, observaciones: pedido.observaciones ?? '' });
    this.form.controls.mesa.disable();
    this.error.set(null);
    this.exito.set(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected cancelarEdicion(): void {
    this.limpiar();
  }

  protected cancelar(pedido: Pedido): void {
    if (!confirm(`¿Cancelar el pedido #${pedido.id} de la mesa ${pedido.numeroMesa}?`)) return;
    this.ejecutar(this.pedidosApi.cancelar(pedido.id), `Pedido #${pedido.id} cancelado.`);
  }

  protected pagar(pedido: Pedido): void {
    if (!confirm(`¿Registrar la venta del pedido #${pedido.id} por ${pesos(pedido.total)}?`)) return;
    this.ejecutar(this.pedidosApi.pagar(pedido.id), `Venta del pedido #${pedido.id} registrada.`);
  }

  protected alternarDetalle(id: number): void {
    this.abierto.update((actual) => (actual === id ? null : id));
  }

  // ---------- Filtros ----------

  protected cambiarFiltroEstado(valor: FiltroEstado): void {
    this.filtroEstado.set(valor);
    this.cargarPedidos();
  }

  protected cambiarFiltroMesa(valor: string): void {
    const mesa = Number(valor);
    this.filtroMesa.set(valor && mesa > 0 ? mesa : null);
    this.cargarPedidos();
  }

  protected cargarPedidos(): void {
    this.pedidosApi.consultar(this.filtroActual()).subscribe({
      next: (lista) => this.pedidos.set(lista),
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }

  protected invalido(campo: 'mesa' | 'observaciones'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && control.touched;
  }

  private filtroActual() {
    return filtroDePedidos(this.filtroEstado(), this.filtroMesa());
  }

  private ejecutar(peticion: ReturnType<PedidoService['cancelar']>, mensaje: string): void {
    this.error.set(null);
    this.exito.set(null);
    peticion.subscribe({
      next: () => {
        this.exito.set(mensaje);
        this.cargarPedidos();
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }
}
