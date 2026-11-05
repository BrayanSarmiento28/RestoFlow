import {
  contarSinVer, enlacePara, esImportante, guardarVistos, haceCuanto, hayUrgenteSinVer, leerVistos, marcarVistos,
  textoCampana,
} from './avisos';
import { Aviso, NivelAviso } from '../models/avisos.models';

function aviso(clave: string, nivel: NivelAviso, enlace: string | null = '/cocina'): Aviso {
  return { clave, tipo: 'PLATO_DEMORADO', nivel, titulo: 'Mesa 4', mensaje: 'Lleva 30 min', enlace, desde: null };
}

/** sessionStorage de mentira para las pruebas. */
function almacenEnMemoria() {
  const datos = new Map<string, string>();
  return {
    getItem: (c: string) => datos.get(c) ?? null,
    setItem: (c: string, v: string) => void datos.set(c, v),
  };
}

describe('RestoFlow AI · campana de avisos', () => {
  const avisos = [aviso('demora-1', 'URGENTE'), aviso('listo-2', 'ATENCION'), aviso('demanda', 'INFO')];

  test('el número de la campana cuenta solo lo importante que no se ha visto', () => {
    expect(contarSinVer(avisos, new Set())).toBe(2);                 // el informativo no cuenta
    expect(contarSinVer(avisos, new Set(['demora-1']))).toBe(1);
    expect(esImportante(avisos[2])).toBe(false);
  });

  test('la campana se resalta mientras haya algo urgente sin ver', () => {
    expect(hayUrgenteSinVer(avisos, new Set())).toBe(true);
    expect(hayUrgenteSinVer(avisos, new Set(['demora-1']))).toBe(false);
  });

  test('al abrirla todo queda visto; si mañana pasa otra vez, vuelve a avisar', () => {
    const vistos = marcarVistos(avisos);
    expect(contarSinVer(avisos, vistos)).toBe(0);
    expect(contarSinVer([aviso('demora-9', 'URGENTE')], vistos)).toBe(1);
  });

  test('solo se muestra el enlace si ese rol puede abrir la pantalla', () => {
    expect(enlacePara('/cocina', 'COCINA')).toBe('/cocina');
    expect(enlacePara('/cocina', 'MESERO')).toBeNull();              // el mesero ve el aviso, sin enlace
    expect(enlacePara('/restoflow-ai', 'INVENTARIO')).toBe('/restoflow-ai');
    expect(enlacePara('/pedidos', 'ADMIN')).toBe('/pedidos');
    expect(enlacePara('/otra', 'ADMIN')).toBeNull();
    expect(enlacePara(null, 'ADMIN')).toBeNull();
    expect(enlacePara('/cocina', null)).toBeNull();
  });

  test('dice hace cuánto pasa', () => {
    const ahora = new Date('2026-11-06T13:00:00-05:00');
    expect(haceCuanto('2026-11-06T12:59:40', ahora)).toBe('ahora');
    expect(haceCuanto('2026-11-06T12:35:00', ahora)).toBe('hace 25 min');
    expect(haceCuanto('2026-11-06T12:00:00', ahora)).toBe('hace 1 h');
    expect(haceCuanto('2026-11-06T11:50:00', ahora)).toBe('hace 1 h 10 min');
    expect(haceCuanto(null, ahora)).toBe('');
  });

  test('texto para lectores de pantalla', () => {
    expect(textoCampana(0)).toBe('Avisos de RestoFlow AI: nada nuevo');
    expect(textoCampana(1)).toBe('Avisos de RestoFlow AI: 1 aviso nuevo');
    expect(textoCampana(3)).toBe('Avisos de RestoFlow AI: 3 avisos nuevos');
  });

  test('recuerda lo visto por usuario y no falla si el navegador no deja guardar', () => {
    const almacen = almacenEnMemoria();
    guardarVistos(almacen, 5, new Set(['demora-1']));
    expect([...leerVistos(almacen, 5)]).toEqual(['demora-1']);
    expect(leerVistos(almacen, 6).size).toBe(0);                      // otro usuario empieza de cero

    const roto = { getItem: () => { throw new Error('bloqueado'); }, setItem: () => { throw new Error('bloqueado'); } };
    expect(leerVistos(roto, 5).size).toBe(0);
    guardarVistos(roto, 5, new Set(['x']));                            // no lanza error
    expect(leerVistos({ getItem: () => '{malo', setItem: () => undefined }, 5).size).toBe(0);
    expect(leerVistos(null, 5).size).toBe(0);
  });
});
