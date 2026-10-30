import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RestoFlowAIService } from '../../core/services/restoflow-ai.service';
import { mensajeDeError } from '../../core/services/errores';
import { hora, numero, pesos } from '../../core/util/formato';
import {
  CLASE_PRIORIDAD, ETIQUETA_TIPO, NOMBRE_DIA, NOMBRE_PRIORIDAD, columnasPorDia, contarPorPrioridad, direccion,
  fechaLarga, insumosPorComprar, mensajeAlertas, textoCobertura, textoHora, textoTendencia,
} from '../../core/util/restoflow-ai';
import { AnalisisRestoFlowAI } from '../../core/models/restoflow-ai.models';
import { ABREVIATURA_UNIDAD, PrioridadAlerta } from '../../core/entities';

type Filtro = 'TODAS' | PrioridadAlerta;

/**
 * RF-09 a RF-11 · RestoFlow AI: recomendaciones, consumo de insumos, predicción de mañana y ventas por día.
 * Todo el análisis lo calcula el backend; esta pantalla solo lo muestra (lógica de presentación en core/util/restoflow-ai.ts).
 */
@Component({
  selector: 'app-restoflow-ai',
  standalone: false,
  templateUrl: './restoflow-ai.html',
  styleUrl: './restoflow-ai.scss',
})
export class RestoFlowAI implements OnInit {
  private readonly api = inject(RestoFlowAIService);

  protected readonly pesos = pesos;
  protected readonly numero = numero;
  protected readonly hora = hora;
  protected readonly abreviatura = ABREVIATURA_UNIDAD;
  protected readonly nombreDia = NOMBRE_DIA;
  protected readonly etiquetaTipo = ETIQUETA_TIPO;
  protected readonly nombrePrioridad = NOMBRE_PRIORIDAD;
  protected readonly clasePrioridad = CLASE_PRIORIDAD;
  protected readonly textoTendencia = textoTendencia;
  protected readonly direccion = direccion;
  protected readonly textoCobertura = textoCobertura;
  protected readonly textoHora = textoHora;
  protected readonly fechaLarga = fechaLarga;
  protected readonly filtros: Filtro[] = ['TODAS', 'ALTA', 'MEDIA', 'BAJA'];

  protected readonly analisis = signal<AnalisisRestoFlowAI | null>(null);
  protected readonly cargando = signal(false);
  protected readonly generando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly filtro = signal<Filtro>('TODAS');

  protected readonly conteo = computed(() => contarPorPrioridad(this.analisis()?.recomendaciones ?? []));
  protected readonly recomendaciones = computed(() => {
    const todas = this.analisis()?.recomendaciones ?? [];
    const f = this.filtro();
    return f === 'TODAS' ? todas : todas.filter((r) => r.prioridad === f);
  });
  protected readonly porComprar = computed(() => insumosPorComprar(this.analisis()?.insumos ?? []));
  protected readonly columnas = computed(() => columnasPorDia(this.analisis()?.ventas.ventasPorDia ?? []));
  protected readonly productos = computed(() => (this.analisis()?.ventas.productos ?? []).slice(0, 5));
  protected readonly promedioDiaFuerte = computed(() => {
    const v = this.analisis()?.ventas;
    return v?.ventasPorDia.find((d) => d.dia === v.diaMasFuerte)?.promedio ?? 0;
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.analisis().subscribe({
      next: (a) => {
        this.analisis.set(a);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeDeError(err));
        this.cargando.set(false);
      },
    });
  }

  /** Lo mismo que hace la tarea programada de cada hora, para no tener que esperarla. */
  protected generarAlertas(): void {
    this.generando.set(true);
    this.mensaje.set(null);
    this.api.generarAlertas().subscribe({
      next: (r) => {
        this.mensaje.set(mensajeAlertas(r));
        this.generando.set(false);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeDeError(err));
        this.generando.set(false);
      },
    });
  }

  protected etiquetaFiltro(f: Filtro): string {
    if (f === 'TODAS') return `Todas (${this.analisis()?.recomendaciones.length ?? 0})`;
    return `${this.nombrePrioridad[f]} (${this.conteo()[f]})`;
  }
}
