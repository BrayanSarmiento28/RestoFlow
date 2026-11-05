import { Component, DestroyRef, ElementRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, timer } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { AvisosService } from '../../core/services/avisos.service';
import { Aviso } from '../../core/models/avisos.models';
import { RespuestaIA } from '../../core/models/restoflow-ai.models';
import { textoOrigen } from '../../core/util/asistente-ia';
import {
  ETIQUETA_NIVEL, INTERVALO_AVISOS_MS, contarSinVer, enlacePara, guardarVistos, haceCuanto, hayUrgenteSinVer,
  leerVistos, marcarVistos, textoCampana,
} from '../../core/util/avisos';

/**
 * RestoFlow AI en toda la plataforma: campana con los avisos del momento para el rol conectado.
 * Pide los avisos cada minuto; el número rojo cuenta los importantes que aún no se han visto. Al abrirla,
 * Gemini (IA externa) escribe un resumen de qué atender primero.
 */
@Component({
  selector: 'app-avisos-campana',
  standalone: false,
  templateUrl: './avisos-campana.html',
  styleUrl: './avisos-campana.scss',
})
export class AvisosCampana implements OnInit {
  private readonly api = inject(AvisosService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly elemento = inject(ElementRef<HTMLElement>);

  protected readonly etiquetaNivel = ETIQUETA_NIVEL;
  protected readonly haceCuanto = haceCuanto;
  protected readonly textoOrigen = textoOrigen;

  protected readonly avisos = signal<Aviso[]>([]);
  protected readonly abierto = signal(false);
  protected readonly sinConexion = signal(false);
  private readonly vistos = signal<Set<string>>(new Set());
  /** Resumen escrito por Gemini (o con reglas si no responde); se pide al abrir la campana. */
  protected readonly resumen = signal<RespuestaIA | null>(null);
  protected readonly pensando = signal(false);

  protected readonly sinVer = computed(() => contarSinVer(this.avisos(), this.vistos()));
  protected readonly urgente = computed(() => hayUrgenteSinVer(this.avisos(), this.vistos()));
  protected readonly texto = computed(() => textoCampana(this.sinVer()));

  private get almacen(): Storage | null {
    try {
      return typeof sessionStorage === 'undefined' ? null : sessionStorage;
    } catch {
      return null;
    }
  }

  ngOnInit(): void {
    this.vistos.set(leerVistos(this.almacen, this.auth.usuario?.id));
    timer(0, INTERVALO_AVISOS_MS)
      .pipe(
        switchMap(() => this.api.consultar().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((r) => {
        this.sinConexion.set(r === null);
        if (r) this.avisos.set(r.avisos);
      });
  }

  protected alternar(): void {
    this.abierto.update((a) => !a);
    if (this.abierto()) {
      const vistos = marcarVistos(this.avisos());
      this.vistos.set(vistos);
      guardarVistos(this.almacen, this.auth.usuario?.id, vistos);
      // Sin avisos no se pide resumen: no hay nada que la IA deba redactar
      if (this.avisos().length > 0) this.pedirResumen();
    }
  }

  private pedirResumen(): void {
    this.pensando.set(true);
    this.api.resumen().subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.pensando.set(false);
      },
      // Sin resumen la campana sigue sirviendo: se muestran solo los avisos
      error: () => this.pensando.set(false),
    });
  }

  protected cerrar(): void {
    this.abierto.set(false);
  }

  protected enlace(a: Aviso): string | null {
    return enlacePara(a.enlace, this.auth.usuario?.rol);
  }

  @HostListener('document:keydown.escape')
  protected alPresionarEscape(): void {
    this.cerrar();
  }

  @HostListener('document:click', ['$event'])
  protected alHacerClic(evento: MouseEvent): void {
    if (this.abierto() && !this.elemento.nativeElement.contains(evento.target as Node)) {
      this.cerrar();
    }
  }
}
