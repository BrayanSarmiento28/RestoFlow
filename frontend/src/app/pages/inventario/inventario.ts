import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { InventarioService } from '../../core/services/inventario.service';
import { AuthService } from '../../core/services/auth.service';
import { mensajeDeError } from '../../core/services/errores';
import { fechaHora, fechaIso, hora, numero } from '../../core/util/formato';
import {
  ABREVIATURA_UNIDAD, Alerta, Ingrediente, MovimientoInventario, NOMBRES_UNIDAD, UnidadMedida,
} from '../../core/entities';

type Accion = 'ENTRADA' | 'SALIDA' | 'AJUSTE';
type Estado = 'agotado' | 'minimo' | 'pronto' | 'revisar' | 'ok';

/** Fila de la tabla: el insumo más lo que se calcula para mostrarlo. */
interface FilaInsumo {
  insumo: Ingrediente;
  estado: Estado;
  /** Días que alcanza el stock con el consumo promedio por ventas de los últimos 7 días (null = sin ventas). */
  dias: number | null;
  /** Ancho de la barra: el mínimo queda en la marca negra (33 %). */
  barra: number;
  icono: string | null;
}

const DIAS_CONSUMO = 7;

/** Imagen según el tipo de insumo (por palabras del nombre); sin coincidencia se muestra la inicial. */
const ICONOS: [RegExp, string][] = [
  [/pollo|pechuga|carne|res|cerdo|chicharr|chorizo|tocino/i, 'carnes'],
  [/pescado|mojarra|trucha|camar|at[uú]n|salm[oó]n/i, 'pescados'],
  [/arroz|fr[ií]jol|lenteja|harina|ma[ií]z|pasta|garbanzo|az[uú]car|sal\b/i, 'granos'],
  [/leche|queso|crema|mantequilla|huevo|yogur/i, 'lacteos'],
  [/papa|yuca|mazorca|pl[aá]tano|tomate|cebolla|aguacate|lechuga|zanahoria|ajo|verdura/i, 'verduras'],
  [/lim[oó]n|naranja|mora|lulo|maracuy|pulpa|fruta|coco|fresa|mango/i, 'frutas'],
];

/** RF-05 a RF-07 · Pantalla del personal de inventario (y del administrador). */
@Component({
  selector: 'app-inventario',
  standalone: false,
  templateUrl: './inventario.html',
  styleUrl: './inventario.scss',
})
export class Inventario implements OnInit {
  private fb = inject(FormBuilder);
  private inventarioApi = inject(InventarioService);
  protected readonly esAdmin = inject(AuthService).tieneRol('ADMIN');

  protected readonly abreviatura = ABREVIATURA_UNIDAD;
  protected readonly nombresUnidad = NOMBRES_UNIDAD;
  protected readonly unidades = Object.keys(NOMBRES_UNIDAD) as UnidadMedida[];
  protected readonly numero = numero;
  protected readonly hora = hora;
  protected readonly fechaHora = fechaHora;

  protected readonly insumos = signal<Ingrediente[]>([]);
  protected readonly movimientos = signal<MovimientoInventario[]>([]);
  protected readonly alertas = signal<Alerta[]>([]);
  protected readonly actualizado = signal<Date | null>(null);
  protected readonly filtro = signal<'todos' | 'alerta'>('todos');
  protected readonly accion = signal<Accion | null>(null);
  protected readonly editando = signal<Ingrediente | 'nuevo' | null>(null);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);

  /** Consumo por ventas de cada insumo en los últimos 7 días (de los movimientos con pedido). */
  private readonly consumoDiario = computed(() => {
    const total = new Map<number, number>();
    this.movimientos()
      .filter((m) => m.tipo === 'SALIDA' && m.pedidoId !== null)
      .forEach((m) => total.set(m.ingredienteId, (total.get(m.ingredienteId) ?? 0) + m.cantidad));
    return new Map([...total].map(([id, cantidad]) => [id, cantidad / DIAS_CONSUMO]));
  });

  protected readonly filas = computed<FilaInsumo[]>(() =>
    this.insumos()
      .filter((i) => i.activo)
      .map((i) => this.fila(i))
      .sort((a, b) => this.urgencia(a) - this.urgencia(b) || a.insumo.nombre.localeCompare(b.insumo.nombre)),
  );
  protected readonly conAlerta = computed(() => this.filas().filter((f) => f.estado !== 'ok' && f.estado !== 'revisar'));
  protected readonly visibles = computed(() => (this.filtro() === 'alerta' ? this.conAlerta() : this.filas()));
  protected readonly inactivos = computed(() => this.insumos().filter((i) => !i.activo));
  protected readonly bajoMinimo = computed(() => this.filas().filter((f) => f.estado === 'agotado' || f.estado === 'minimo'));
  /** Alcanzan para 3 días o menos según las ventas (incluye los que ya están bajo el mínimo). */
  protected readonly porAgotarse = computed(() => this.filas().filter((f) => f.dias !== null && f.dias <= 3));
  /** El insumo más urgente, para el aviso de arriba. */
  protected readonly urgente = computed(() => this.bajoMinimo()[0] ?? null);

  protected readonly movimientosHoy = computed(() => {
    const hoy = fechaIso(new Date());
    const deHoy = this.movimientos().filter((m) => m.fecha.startsWith(hoy));
    return {
      total: deHoy.length,
      ventas: deHoy.filter((m) => m.pedidoId !== null).length,
      entradas: deHoy.filter((m) => m.tipo === 'ENTRADA').length,
    };
  });
  protected readonly recientes = computed(() => this.movimientos().slice(0, 8));

  protected readonly formMovimiento = this.fb.group({
    ingredienteId: [null as number | null, Validators.required],
    cantidad: [null as number | null, [Validators.required, Validators.min(0)]],
    motivo: ['', Validators.maxLength(120)],
  });

  protected readonly formInsumo = this.fb.group({
    nombre: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    unidad: ['GRAMO' as UnidadMedida, Validators.required],
    stockMinimo: [null as number | null, [Validators.required, Validators.min(0)]],
    stockInicial: [null as number | null, Validators.min(0)],
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    const desde = new Date();
    desde.setDate(desde.getDate() - (DIAS_CONSUMO - 1));
    forkJoin({
      insumos: this.inventarioApi.ingredientes(),
      movimientos: this.inventarioApi.movimientos({ desde: fechaIso(desde) }),
      alertas: this.inventarioApi.alertas(true),
    }).subscribe({
      next: ({ insumos, movimientos, alertas }) => {
        this.insumos.set(insumos);
        this.movimientos.set(movimientos);
        this.alertas.set(alertas);
        this.actualizado.set(new Date());
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }

  // ---------- Entradas, salidas y ajustes ----------

  protected abrirMovimiento(accion: Accion, insumo?: Ingrediente): void {
    this.editando.set(null);
    this.accion.set(accion);
    this.limpiarMensajes();
    this.formMovimiento.reset({ ingredienteId: insumo?.id ?? null, cantidad: null, motivo: '' });
    const motivo = this.formMovimiento.controls.motivo;
    motivo.setValidators(accion === 'SALIDA' ? [Validators.required, Validators.maxLength(120)] : Validators.maxLength(120));
    motivo.updateValueAndValidity();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected unidadSeleccionada(): string {
    const id = this.formMovimiento.controls.ingredienteId.value;
    const insumo = this.insumos().find((i) => i.id === id);
    return insumo ? this.abreviatura[insumo.unidad] : '';
  }

  protected guardarMovimiento(): void {
    const accion = this.accion();
    if (!accion || this.formMovimiento.invalid) {
      this.formMovimiento.markAllAsTouched();
      return;
    }
    const v = this.formMovimiento.getRawValue();
    const id = Number(v.ingredienteId);
    const cantidad = Number(v.cantidad);
    const motivo = v.motivo?.trim() || null;
    if (accion !== 'AJUSTE' && cantidad <= 0) {
      this.error.set('La cantidad debe ser mayor que cero.');
      return;
    }
    const peticion = accion === 'ENTRADA'
      ? this.inventarioApi.entrada(id, { cantidad, motivo })
      : accion === 'SALIDA'
        ? this.inventarioApi.salida(id, { cantidad, motivo })
        : this.inventarioApi.ajuste(id, { stockContado: cantidad, motivo });
    this.guardando.set(true);
    this.limpiarMensajes();
    peticion.subscribe({
      next: (i) => {
        this.guardando.set(false);
        this.accion.set(null);
        this.exito.set(`${i.nombre}: ahora hay ${numero(i.stockActual)} ${this.abreviatura[i.unidad]}.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(err));
      },
    });
  }

  // ---------- Crear y editar insumos ----------

  protected abrirInsumo(insumo?: Ingrediente): void {
    this.accion.set(null);
    this.editando.set(insumo ?? 'nuevo');
    this.limpiarMensajes();
    this.formInsumo.reset({
      nombre: insumo?.nombre ?? '',
      unidad: insumo?.unidad ?? 'GRAMO',
      stockMinimo: insumo?.stockMinimo ?? null,
      stockInicial: null,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected guardarInsumo(): void {
    if (this.formInsumo.invalid) {
      this.formInsumo.markAllAsTouched();
      return;
    }
    const v = this.formInsumo.getRawValue();
    const datos = {
      nombre: (v.nombre ?? '').trim(),
      unidad: v.unidad as UnidadMedida,
      stockMinimo: Number(v.stockMinimo),
      stockInicial: v.stockInicial === null ? null : Number(v.stockInicial),
    };
    const actual = this.editando();
    const peticion = actual && actual !== 'nuevo'
      ? this.inventarioApi.editar(actual.id, datos)
      : this.inventarioApi.crear(datos);
    this.guardando.set(true);
    this.limpiarMensajes();
    peticion.subscribe({
      next: (i) => {
        this.guardando.set(false);
        this.editando.set(null);
        this.exito.set(actual === 'nuevo' ? `Insumo ${i.nombre} creado.` : `Insumo ${i.nombre} actualizado.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(err));
      },
    });
  }

  protected alternarEstado(insumo: Ingrediente): void {
    this.limpiarMensajes();
    this.inventarioApi.cambiarEstado(insumo.id, !insumo.activo).subscribe({
      next: (i) => {
        this.editando.set(null);
        this.exito.set(`${i.nombre} ${i.activo ? 'activado' : 'desactivado (su historial se conserva)'}.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }

  protected cancelar(): void {
    this.accion.set(null);
    this.editando.set(null);
  }

  // ---------- Alertas ----------

  protected atender(alerta: Alerta): void {
    this.inventarioApi.atender(alerta.id).subscribe({
      next: () => this.alertas.update((lista) => lista.filter((a) => a.id !== alerta.id)),
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }

  // ---------- Ayudas para la plantilla ----------

  protected invalido(form: 'movimiento' | 'insumo', campo: string): boolean {
    const grupo: FormGroup = form === 'movimiento' ? this.formMovimiento : this.formInsumo;
    const control = grupo.get(campo);
    return !!control && control.invalid && control.touched;
  }

  protected cantidadMovimiento(m: MovimientoInventario): string {
    const signo = m.tipo === 'ENTRADA' ? '+' : m.tipo === 'SALIDA' ? '−' : '= ';
    return `${signo}${numero(m.cantidad)} ${this.abreviatura[m.unidad]}`;
  }

  protected detalleMovimiento(m: MovimientoInventario): string {
    if (m.pedidoId !== null) return `Venta · pedido #${m.pedidoId}`;
    return [m.motivo, m.usuarioNombre].filter(Boolean).join(' · ');
  }

  /** "Arroz, Huevo y 2 más" */
  protected nombres(filas: FilaInsumo[]): string {
    const lista = filas.map((f) => f.insumo.nombre);
    return lista.length <= 3 ? lista.join(', ') : `${lista.slice(0, 2).join(', ')} y ${lista.length - 2} más`;
  }

  protected nombreInsumo(): string {
    const actual = this.editando();
    return actual && actual !== 'nuevo' ? actual.nombre : '';
  }

  protected esEdicion(): boolean {
    const actual = this.editando();
    return !!actual && actual !== 'nuevo';
  }

  private fila(i: Ingrediente): FilaInsumo {
    const consumo = this.consumoDiario().get(i.id) ?? 0;
    const dias = consumo > 0 ? i.stockActual / consumo : null;
    let estado: Estado = 'ok';
    if (i.agotado) estado = 'agotado';
    else if (i.enMinimo) estado = 'minimo';
    else if (dias !== null && dias <= 3) estado = 'pronto';
    else if (i.stockActual <= i.stockMinimo * 1.5) estado = 'revisar';
    const escala = i.stockMinimo > 0 ? i.stockMinimo * 3 : Math.max(i.stockActual, 1);
    const icono = ICONOS.find(([patron]) => patron.test(i.nombre))?.[1] ?? null;
    return {
      insumo: i, estado, dias,
      barra: Math.min(100, (i.stockActual / escala) * 100),
      icono: icono ? `img/insumos/${icono}.svg` : null,
    };
  }

  private urgencia(f: FilaInsumo): number {
    return ['agotado', 'minimo', 'pronto', 'revisar', 'ok'].indexOf(f.estado);
  }

  private limpiarMensajes(): void {
    this.error.set(null);
    this.exito.set(null);
  }
}
