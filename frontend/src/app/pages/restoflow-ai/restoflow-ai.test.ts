// Pruebas con Jest · pantalla RestoFlow AI (análisis, recomendaciones de Gemini, chat y alertas anticipadas).
import { Observable } from 'rxjs';
import { RestoFlowAI } from './restoflow-ai';
import { RecomendacionesIA } from '../../core/models/restoflow-ai.models';
import { RestoFlowAIService } from '../../core/services/restoflow-ai.service';
import { Montaje, conSesion, crear, falla, ok } from '../../../testing/angular';
import { analisis, recomendacion } from '../../../testing/datos';

function apiIA() {
  return {
    analisis: jest.fn(() => ok(analisis())),
    recomendacionesIA: jest.fn((): Observable<RecomendacionesIA> => ok({
      origen: 'GEMINI', generadoEn: '2026-11-05T08:00:00',
      recomendaciones: [recomendacion({ titulo: 'Gemini: compre arroz hoy' })],
    })),
    preguntar: jest.fn(() => ok({ texto: 'Compre arroz y huevos.', origen: 'GEMINI' as const, generadoEn: '' })),
    generarAlertas: jest.fn(() => ok({ creadas: 2, cerradas: 1, yaAvisados: 0 })),
  };
}

describe('Pantalla RestoFlow AI', () => {
  let montaje: Montaje<RestoFlowAI>;
  let api: ReturnType<typeof apiIA>;

  function abrir(rol: 'ADMIN' | 'INVENTARIO' = 'ADMIN', preparar?: (a: ReturnType<typeof apiIA>) => void) {
    api = apiIA();
    preparar?.(api);
    montaje = crear(RestoFlowAI, [{ provide: RestoFlowAIService, useValue: api }, conSesion(rol)]);
    montaje.componente.ngOnInit();
    return montaje.componente;
  }

  afterEach(() => montaje.destruir());

  it('carga el análisis y luego las recomendaciones redactadas por Gemini', () => {
    const c = abrir();
    expect(c['analisis']()).not.toBeNull();
    expect(c['cargando']()).toBe(false);
    expect(c['cargandoIA']()).toBe(false);
    expect(c['todas']().map((r) => r.titulo)).toEqual(['Gemini: compre arroz hoy']);
    expect(c['esAdmin']).toBe(true);
  });

  it('si Gemini no responde, se quedan las recomendaciones calculadas con reglas', () => {
    const c = abrir('INVENTARIO', (a) => a.recomendacionesIA.mockReturnValue(falla(503)));
    expect(c['todas']()).toHaveLength(3);
    expect(c['cargandoIA']()).toBe(false);
    expect(c['esAdmin']).toBe(false);
  });

  it('las recomendaciones de reglas también se usan cuando el origen es REGLAS', () => {
    const c = abrir('ADMIN', (a) => a.recomendacionesIA.mockReturnValue(ok({
      origen: 'REGLAS', generadoEn: '', recomendaciones: [] })));
    expect(c['todas']()).toHaveLength(3);
  });

  it('filtra por prioridad y cuenta cuántas hay de cada una', () => {
    const c = abrir('ADMIN', (a) => a.recomendacionesIA.mockReturnValue(falla(503)));
    expect(c['etiquetaFiltro']('TODAS')).toBe('Todas (3)');
    expect(c['etiquetaFiltro']('ALTA')).toBe('Alta (1)');
    c['filtro'].set('MEDIA');
    expect(c['recomendaciones']().map((r) => r.titulo)).toEqual(['Sube la bandeja']);
    c['filtro'].set('TODAS');
    expect(c['recomendaciones']()).toHaveLength(3);
  });

  it('prepara los datos de la pantalla: compras, columnas por día, productos y el día más fuerte', () => {
    const c = abrir();
    expect(c['porComprar']().map((i) => i.nombre)).toEqual(['Arroz']);
    expect(c['columnas']()).toHaveLength(2);
    expect(c['productos']()).toHaveLength(1);
    expect(c['promedioDiaFuerte']()).toBe(300000);
  });

  it('sin análisis todo queda vacío y el error del API se muestra', () => {
    const c = abrir('ADMIN', (a) => a.analisis.mockReturnValue(falla(500, 'No se pudo analizar')));
    expect(c['error']()).toBe('No se pudo analizar');
    expect(c['cargando']()).toBe(false);
    expect(c['todas']()).toEqual([]);
    expect(c['porComprar']()).toEqual([]);
    expect(c['productos']()).toEqual([]);
    expect(c['promedioDiaFuerte']()).toBe(0);
  });

  it('el administrador le pregunta a RestoFlow AI y la respuesta queda arriba en la conversación', () => {
    const c = abrir();
    c['pregunta'].set('   ');
    c['preguntar']();
    expect(api.preguntar).not.toHaveBeenCalled();

    c['usarSugerencia']('¿Qué debo comprar esta semana?');
    expect(api.preguntar).toHaveBeenCalledWith('¿Qué debo comprar esta semana?');
    expect(c['conversacion']()[0]).toMatchObject({ pregunta: '¿Qué debo comprar esta semana?', respuesta: 'Compre arroz y huevos.', error: null });
    expect(c['pregunta']()).toBe('');
    expect(c['enviando']()).toBe(false);
  });

  it('si Gemini no está disponible, la conversación muestra el error', () => {
    const c = abrir();
    api.preguntar.mockReturnValue(falla(503, 'Gemini no está disponible en este momento.'));
    c['pregunta'].set('¿Cómo vamos?');
    expect(c['puedeEnviar']()).toBe(true);
    c['preguntar']();
    expect(c['conversacion']()[0]).toMatchObject({ respuesta: null, error: 'Gemini no está disponible en este momento.' });
    expect(c['pregunta']()).toBe('¿Cómo vamos?');
  });

  it('genera las alertas anticipadas al momento y vuelve a cargar', () => {
    const c = abrir();
    c['generarAlertas']();
    expect(c['mensaje']()).toContain('RestoFlow AI revisó el inventario: 2 alertas nuevas y 1 cerrada.');
    expect(c['generando']()).toBe(false);
    expect(api.analisis).toHaveBeenCalledTimes(2);
    api.generarAlertas.mockReturnValue(falla(403));
    c['generarAlertas']();
    expect(c['error']()).toBe('Tu rol no tiene permiso para esta acción.');
  });
});
