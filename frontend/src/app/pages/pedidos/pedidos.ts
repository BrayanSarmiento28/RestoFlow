import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CatalogoService } from '../../core/services/catalogo.service';
import { PedidoService } from '../../core/services/pedido.service';
import { mensajeDeError } from '../../core/services/errores';
import { hora, pesos } from '../../core/util/formato';
import {
  EstadoCocina, NOMBRES_ESTADO_COCINA, NOMBRES_ESTADO_PEDIDO, Pedido, Producto,
} from '../../core/entities';
import { FiltroPedidos } from '../../core/models/pedido.models';
import { environment } from '../../../environments/environment';

/** Una línea del pedido que el mesero está armando (todavía no se ha enviado). */
interface ItemCarrito {
  producto: Producto;
  cantidad: number;
  nota: string;
}

type FiltroEstado = 'ACTIVOS' | EstadoCocina | 'PAGADO' | 'CANCELADO' | 'TODOS';

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
  protected readonly categorias = computed(() =>
    ['Todas', ...new Set(this.productos().map((p) => p.categoriaNombre))]);
  protected readonly productosVisibles = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.productos().filter((p) =>
      (this.categoriaActiva() === 'Todas' || p.categoriaNombre === this.categoriaActiva())
      && (!texto || p.nombre.toLowerCase().includes(texto)));
  });

  // ---------- Pedido en construcción ----------
  protected readonly carrito = signal<ItemCarrito[]>([]);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly total = computed(() =>
    this.carrito().reduce((suma, item) => suma + item.producto.precio * item.cantidad, 0));
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
    const existente = this.carrito().find((i) => i.producto.id === producto.id);
    if (existente) {
      this.cambiarCantidad(existente, 1);
    } else {
      this.carrito.update((items) => [...items, { producto, cantidad: 1, nota: '' }]);
    }
  }

  protected cambiarCantidad(item: ItemCarrito, cambio: number): void {
    const nueva = item.cantidad + cambio;
    if (nueva < 1) {
      this.quitar(item);
      return;
    }
    this.carrito.update((items) => items.map((i) => (i === item ? { ...i, cantidad: Math.min(nueva, 99) } : i)));
  }

  protected cambiarNota(item: ItemCarrito, nota: string): void {
    this.carrito.update((items) => items.map((i) => (i === item ? { ...i, nota } : i)));
  }

  protected quitar(item: ItemCarrito): void {
    this.carrito.update((items) => items.filter((i) => i !== item));
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
    const datos = {
      numeroMesa: Number(valores.mesa),
      observaciones: valores.observaciones?.trim() || null,
      lineas: this.carrito().map((i) => ({ productoId: i.producto.id, cantidad: i.cantidad, nota: i.nota.trim() || null })),
    };
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
    const items: ItemCarrito[] = [];
    for (const linea of pedido.lineas) {
      const producto = this.productos().find((p) => p.id === linea.productoId);
      if (!producto) {
        this.error.set(`El producto ${linea.productoNombre} ya no está disponible; registra un pedido nuevo.`);
        return;
      }
      items.push({ producto, cantidad: linea.cantidad, nota: linea.nota ?? '' });
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

  private filtroActual(): FiltroPedidos {
    const estado = this.filtroEstado();
    const filtro: FiltroPedidos = { mesa: this.filtroMesa() };
    if (estado === 'ACTIVOS') filtro.estadoPedido = 'ABIERTO';
    else if (estado === 'PAGADO' || estado === 'CANCELADO') filtro.estadoPedido = estado;
    else if (estado !== 'TODOS') filtro.estadoCocina = estado;
    return filtro;
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
