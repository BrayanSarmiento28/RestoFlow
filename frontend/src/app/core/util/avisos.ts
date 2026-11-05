// RestoFlow AI · lógica de la campana de avisos, sin Angular (se prueba con Jest en avisos.test.ts).
import type { Rol } from '../entities/usuario';
import type { Aviso, NivelAviso } from '../models/avisos.models';

/** Cada cuánto se vuelven a pedir los avisos (1 minuto: los tiempos de cocina se miden en minutos). */
export const INTERVALO_AVISOS_MS = 60_000;

export const ETIQUETA_NIVEL: Record<NivelAviso, string> = {
  URGENTE: 'Urgente',
  ATENCION: 'Atención',
  INFO: 'Información',
};

/** Las pantallas a las que lleva un aviso y quién puede abrirlas (las mismas reglas de las rutas). */
const ROLES_POR_PANTALLA: Record<string, readonly Rol[]> = {
  '/cocina': ['COCINA', 'ADMIN'],
  '/pedidos': ['MESERO', 'ADMIN'],
  '/inventario': ['INVENTARIO', 'ADMIN'],
  '/restoflow-ai': ['INVENTARIO', 'ADMIN'],
};

/** El enlace del aviso solo si ese rol puede abrir la pantalla; si no, el aviso se muestra sin enlace. */
export function enlacePara(enlace: string | null, rol: Rol | null | undefined): string | null {
  if (!enlace || !rol) return null;
  const roles = ROLES_POR_PANTALLA[enlace];
  return roles && roles.includes(rol) ? enlace : null;
}

/** Los informativos no cuentan en el número rojo de la campana. */
export function esImportante(aviso: Aviso): boolean {
  return aviso.nivel !== 'INFO';
}

/** Cuántos avisos importantes no se han visto todavía (el número de la campana). */
export function contarSinVer(avisos: Aviso[], vistos: ReadonlySet<string>): number {
  return avisos.filter((a) => esImportante(a) && !vistos.has(a.clave)).length;
}

/** Hay algo urgente que no se ha visto: la campana se resalta. */
export function hayUrgenteSinVer(avisos: Aviso[], vistos: ReadonlySet<string>): boolean {
  return avisos.some((a) => a.nivel === 'URGENTE' && !vistos.has(a.clave));
}

/**
 * Al abrir la campana todo lo actual queda visto. Solo se guardan las claves que siguen activas: lo que ya se
 * resolvió se olvida, y si vuelve a pasar se avisa de nuevo.
 */
export function marcarVistos(avisos: Aviso[]): Set<string> {
  return new Set(avisos.map((a) => a.clave));
}

/** "hace 5 min", "hace 1 h 10 min", "ahora". */
export function haceCuanto(desde: string | null, ahora: Date = new Date()): string {
  if (!desde) return '';
  const minutos = Math.max(0, Math.floor((ahora.getTime() - new Date(desde).getTime()) / 60_000));
  if (minutos < 1) return 'ahora';
  if (minutos < 60) return `hace ${minutos} min`;
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  return resto ? `hace ${horas} h ${resto} min` : `hace ${horas} h`;
}

/** Texto para lectores de pantalla y el título del botón. */
export function textoCampana(sinVer: number): string {
  if (sinVer === 0) return 'Avisos de RestoFlow AI: nada nuevo';
  return `Avisos de RestoFlow AI: ${sinVer} ${sinVer === 1 ? 'aviso nuevo' : 'avisos nuevos'}`;
}

// ---------- Vistos en esta sesión del navegador (sessionStorage) ----------

interface Almacen {
  getItem(clave: string): string | null;
  setItem(clave: string, valor: string): void;
}

const PREFIJO = 'restoflow.avisos.vistos.';

/** Si el navegador no deja guardar (modo privado, bloqueado), simplemente empieza vacío. */
export function leerVistos(almacen: Almacen | null | undefined, usuarioId: number | undefined): Set<string> {
  try {
    const texto = almacen?.getItem(PREFIJO + (usuarioId ?? 'anonimo'));
    const lista = texto ? JSON.parse(texto) : [];
    return new Set(Array.isArray(lista) ? lista.filter((c) => typeof c === 'string') : []);
  } catch {
    return new Set();
  }
}

export function guardarVistos(almacen: Almacen | null | undefined, usuarioId: number | undefined,
                              vistos: ReadonlySet<string>): void {
  try {
    almacen?.setItem(PREFIJO + (usuarioId ?? 'anonimo'), JSON.stringify([...vistos]));
  } catch {
    // Sin almacenamiento la campana sigue funcionando; solo se olvida qué se vio al recargar
  }
}
