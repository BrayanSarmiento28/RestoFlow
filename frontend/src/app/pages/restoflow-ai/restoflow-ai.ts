import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RestoFlowAIService } from '../../core/services/restoflow-ai.service';
import { mensajeDeError } from '../../core/services/errores';
import { hora, numero, pesos } from '../../core/util/formato';
import {
  CLASE_PRIORIDAD, ETIQUETA_TIPO, NOMBRE_DIA, NOMBRE_PRIORIDAD, columnasPorDia, contarPorPrioridad, direccion,
  fechaLarga, insumosPorComprar, mensajeAlertas, textoCobertura, textoHora, textoTendencia,
} from '../../core/util/restoflow-ai';
import { AnalisisRestoFlowAI, RecomendacionesIA } from '../../core/models/restoflow-ai.models';
import { AuthService } from '../../core/services/auth.service';
import {
  Intercambio, SUGERENCIAS, agregarIntercambio, contador, puedePreguntar, textoOrigen,
} from '../../core/util/asistente-ia';
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
  private readonly auth = inject(AuthService);

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
  protected readonly textoOrigen = textoOrigen;
  protected readonly contador = contador;
  protected readonly sugerencias = SUGERENCIAS;
  /** El chat con Gemini es solo del administrador (el backend también lo exige). */
  protected readonly esAdmin = this.auth.tieneRol('ADMIN');

  protected readonly analisis = signal<AnalisisRestoFlowAI | null>(null);
  protected readonly cargando = signal(false);
  protected readonly generando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly filtro = signal<Filtro>('TODAS');

  /** Recomendaciones redactadas por Gemini; si no llegan, se muestran las calculadas con reglas. */
  protected readonly deIA = signal<RecomendacionesIA | null>(null);
  protected readonly cargandoIA = signal(false);
  protected readonly todas = computed(() => {
    const ia = this.deIA();
    return ia?.origen === 'GEMINI' ? ia.recomendaciones : (this.analisis()?.recomendaciones ?? []);
  });

  // Pregúntale a RestoFlow AI
  protected readonly pregunta = signal('');
  protected readonly enviando = signal(false);
  protected readonly conversacion = signal<Intercambio[]>([]);
  protected readonly puedeEnviar = computed(() => puedePreguntar(this.pregunta(), this.enviando()));

  protected readonly conteo = computed(() => contarPorPrioridad(this.todas()));
  protected readonly recomendaciones = computed(() => {
    const todas = this.todas();
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
        this.cargarIA();
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeDeError(err));
        this.cargando.set(false);
      },
    });
  }

  /** Gemini puede tardar unos segundos: mientras tanto se ven las recomendaciones de reglas. */
  private cargarIA(): void {
    this.cargandoIA.set(true);
    this.api.recomendacionesIA().subscribe({
      next: (r) => {
        this.deIA.set(r);
        this.cargandoIA.set(false);
      },
      error: () => this.cargandoIA.set(false),
    });
  }

  protected usarSugerencia(texto: string): void {
    this.pregunta.set(texto);
    this.preguntar();
  }

  protected preguntar(): void {
    const texto = this.pregunta().trim();
    if (!puedePreguntar(texto, this.enviando())) return;
    this.enviando.set(true);
    const hora = new Date().toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' });
    this.api.preguntar(texto).subscribe({
      next: (r) => {
        this.conversacion.update((l) => agregarIntercambio(l, { pregunta: texto, respuesta: r.texto, error: null, hora }));
        this.pregunta.set('');
        this.enviando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.conversacion.update((l) => agregarIntercambio(l,
          { pregunta: texto, respuesta: null, error: mensajeDeError(err), hora }));
        this.enviando.set(false);
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
    if (f === 'TODAS') return `Todas (${this.todas().length})`;
    return `${this.nombrePrioridad[f]} (${this.conteo()[f]})`;
  }
}
