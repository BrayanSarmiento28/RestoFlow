import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CatalogoService } from '../../core/services/catalogo.service';
import { PedidoService } from '../../core/services/pedido.service';
import { mensajeDeError } from '../../core/services/errores';
import { hora, pesos } from '../../core/util/formato';
import {
  MetodoPago, NOMBRES_ESTADO_COCINA, NOMBRES_ESTADO_PEDIDO, NOMBRES_METODO_PAGO, Pedido, Producto, numeroPedido,
} from '../../core/entities';
import { environment } from '../../../environments/environment';
import {
  FiltroEstado, ItemCarrito, aPedidoRequest, agregarProducto, cambiarCantidad, cambiarNota, carritoDesdePedido,
  categoriasDe, filtrarProductos, filtroDePedidos, quitarItem, totalCarrito,
} from '../../core/util/carrito';
import { errorCambioMesa, mensajeMesaOcupada, mesaOcupada, mesasConMesero, nombrePedido } from '../../core/util/mesas';
import { AuthService } from '../../core/services/auth.service';
import { MesaOcupada } from '../../core/models/pedido.models';

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
  private readonly fb = inject(FormBuilder);
  private readonly catalogoApi = inject(CatalogoService);
  private readonly pedidosApi = inject(PedidoService);
  private readonly auth = inject(AuthService);
  /** El número del pedido (#275) solo lo ve el administrador; el mesero identifica el pedido por la mesa. */
  protected readonly verNumero = this.auth.tieneRol('ADMIN');
  /** #1, #2... del día (el id interno solo en pedidos antiguos). */
  protected readonly numeroPedido = numeroPedido;
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
  protected readonly editandoNumero = signal<number | null>(null);
  protected readonly total = computed(() => totalCarrito(this.carrito()));
  protected readonly form = this.fb.group({
    mesa: [null as number | null, [Validators.required, Validators.min(1), Validators.max(999)]],
    observaciones: ['', Validators.maxLength(300)],
  });

  // ---------- Mesas ocupadas (RN: una mesa con pedido abierto no recibe otro) ----------
  protected readonly ocupadas = signal<MesaOcupada[]>([]);
  protected readonly mesasOcupadas = computed(() => mesasConMesero(this.ocupadas()));
  private readonly mesaEscrita = toSignal(this.form.controls.mesa.valueChanges, { initialValue: null });
  /** Aviso debajo del campo Mesa mientras se arma un pedido nuevo (al editar, la mesa es la del propio pedido). */
  protected readonly avisoMesa = computed(() => {
    if (this.editandoId()) return null;
    const ocupada = mesaOcupada(this.ocupadas(), this.mesaEscrita());
    return ocupada ? mensajeMesaOcupada(ocupada) : null;
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
    this.editandoNumero.set(null);
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
    if (this.avisoMesa()) {
      this.error.set(this.avisoMesa());
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
          ? `${this.nombre(pedido)} actualizado.`
          : `${this.nombre(pedido)} enviado a cocina.`);
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
    this.editandoNumero.set(numeroPedido(pedido));
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
    if (!confirm(`¿Cancelar el ${nombrePedido(numeroPedido(pedido), pedido.numeroMesa, this.verNumero)}?`)) return;
    this.ejecutar(this.pedidosApi.cancelar(pedido.id), `${this.nombre(pedido)} cancelado.`);
  }

  /** "Pedido #275 de la mesa 5" para el admin; "Pedido de la mesa 5" para el mesero. */
  private nombre(pedido: Pedido): string {
    const texto = nombrePedido(numeroPedido(pedido), pedido.numeroMesa, this.verNumero);
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  /** El cliente se cambió de lugar: el pedido pasa a otra mesa libre (la anterior queda libre). */
  protected cambiarMesa(pedido: Pedido): void {
    const texto = prompt(`¿A qué mesa se pasa el ${nombrePedido(numeroPedido(pedido), pedido.numeroMesa, this.verNumero)}?`);
    if (texto === null) return;
    const problema = errorCambioMesa(texto, pedido.numeroMesa, this.ocupadas());
    if (problema) {
      this.exito.set(null);
      this.error.set(problema);
      return;
    }
    const nueva = Number(texto.trim());
    this.ejecutar(this.pedidosApi.cambiarMesa(pedido.id, nueva),
      `${this.nombre(pedido)} pasado a la mesa ${nueva}.`);
  }

  // ---------- Registrar la venta (ventana con el método de pago) ----------

  protected readonly metodosPago: MetodoPago[] = ['EFECTIVO', 'TRANSFERENCIA'];
  protected readonly nombresMetodo = NOMBRES_METODO_PAGO;
  /** Pedido que se está cobrando (abre la ventana) y el método elegido. */
  protected readonly cobrando = signal<Pedido | null>(null);
  protected readonly metodoElegido = signal<MetodoPago | null>(null);
  protected readonly cobrandoEnvio = signal(false);

  /** Admin: registrar la venta. Mesero: pasar la cuenta (ver, imprimir; el cobro lo hace el administrador). */
  protected pagar(pedido: Pedido): void {
    this.metodoElegido.set(null);
    this.cobrando.set(pedido);
  }

  /** "4 nov 2026, 9:30 p. m." en la cuenta impresa. */
  protected fechaCuenta(): string {
    return new Date().toLocaleString('es-CO', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
  }

  /** Abre el cuadro de impresión del navegador; solo se imprime la cuenta (estilos @media print). */
  protected imprimirCuenta(): void {
    window.print();
  }

  @HostListener('document:keydown.escape')
  protected cerrarCobro(): void {
    if (this.cobrandoEnvio()) return;
    this.cobrando.set(null);
  }

  protected confirmarCobro(): void {
    const pedido = this.cobrando();
    const metodo = this.metodoElegido();
    if (!pedido || !metodo || !this.verNumero || this.cobrandoEnvio()) return;
    this.cobrandoEnvio.set(true);
    this.error.set(null);
    this.exito.set(null);
    this.pedidosApi.pagar(pedido.id, metodo).subscribe({
      next: () => {
        this.cobrandoEnvio.set(false);
        this.cobrando.set(null);
        this.exito.set(`Venta del ${nombrePedido(numeroPedido(pedido), pedido.numeroMesa, this.verNumero)} `
          + `registrada (${NOMBRES_METODO_PAGO[metodo].toLowerCase()}). La mesa quedó libre.`);
        this.cargarPedidos();
      },
      error: (err: HttpErrorResponse) => {
        this.cobrandoEnvio.set(false);
        this.cobrando.set(null);
        this.error.set(mensajeDeError(err));
      },
    });
  }


  /** Accesibilidad: con el teclado (Enter o Espacio) la fila se abre igual que con un clic. */
  protected teclaEnFila(evento: KeyboardEvent, id: number): void {
    if (evento.key === 'Enter' || evento.key === ' ') {
      evento.preventDefault();
      this.alternarDetalle(id);
    }
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
    // Se actualizan junto con la lista: otro mesero pudo ocupar o liberar una mesa
    this.pedidosApi.mesasOcupadas().subscribe({ next: (m) => this.ocupadas.set(m) });
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
