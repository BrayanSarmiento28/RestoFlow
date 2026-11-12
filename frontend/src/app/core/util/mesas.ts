// RN · Mesas ocupadas: una mesa con un pedido abierto no recibe otro hasta que se cobre, se cancele o el pedido se
// cambie de mesa. Funciones puras (sin Angular), probadas con Jest en mesas.test.ts. El backend aplica la misma regla.
import { NOMBRES_ESTADO_COCINA } from '../entities';
import type { MesaOcupada } from '../models/pedido.models';

/** La mesa ocupada con ese número, o null si está libre (o si el número no es válido). */
export function mesaOcupada(ocupadas: MesaOcupada[], numero: number | null | undefined): MesaOcupada | null {
  if (!numero || numero < 1) return null;
  return ocupadas.find((m) => m.numeroMesa === numero) ?? null;
}

/** "La mesa 5 está ocupada (la atiende Laura · En preparación)...". Sin el número del pedido (solo es del admin). */
export function mensajeMesaOcupada(m: MesaOcupada): string {
  return `La mesa ${m.numeroMesa} está ocupada (la atiende ${m.meseroNombre} · `
    + `${NOMBRES_ESTADO_COCINA[m.estadoCocina]}). Se libera cuando su pedido se cobre o se cancele, o cuando se `
    + 'cambie a otra mesa.';
}

/**
 * Cómo se nombra un pedido en la pantalla. El número (#275) solo lo ve el administrador; el mesero y la cocina lo
 * identifican por la mesa, que basta porque una mesa tiene un solo pedido abierto.
 */
export function nombrePedido(id: number, mesa: number, verNumero: boolean): string {
  return verNumero ? `pedido #${id} de la mesa ${mesa}` : `pedido de la mesa ${mesa}`;
}

/** Las mesas ocupadas en orden, con el mesero que la atiende: [{ mesa: 5, mesero: "Laura" }, ...]. */
export function mesasConMesero(ocupadas: MesaOcupada[]): { mesa: number; mesero: string; texto: string }[] {
  return [...ocupadas]
    .sort((a, b) => a.numeroMesa - b.numeroMesa)
    .map((m) => ({ mesa: m.numeroMesa, mesero: m.meseroNombre, texto: `Mesa ${m.numeroMesa} · ${m.meseroNombre}` }));
}

/**
 * Revisa el número que escribió el mesero para cambiar un pedido de mesa. Devuelve el error a mostrar, o null si
 * se puede. texto = lo que escribió (null si canceló el cuadro).
 */
export function errorCambioMesa(texto: string | null, actual: number, ocupadas: MesaOcupada[]): string | null {
  if (texto === null) return null;
  const nueva = Number(texto.trim());
  if (!Number.isInteger(nueva) || nueva < 1 || nueva > 999) return 'Escriba un número de mesa entre 1 y 999.';
  if (nueva === actual) return `El pedido ya está en la mesa ${actual}.`;
  const ocupada = mesaOcupada(ocupadas, nueva);
  return ocupada ? mensajeMesaOcupada(ocupada) : null;
}
