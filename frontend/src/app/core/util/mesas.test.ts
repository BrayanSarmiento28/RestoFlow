import { errorCambioMesa, mensajeMesaOcupada, mesaOcupada, mesasConMesero, nombrePedido } from './mesas';
import { MesaOcupada } from '../models/pedido.models';

const OCUPADAS: MesaOcupada[] = [
  { numeroMesa: 7, pedidoId: 280, meseroNombre: 'Pedro', estadoCocina: 'LISTO' },
  { numeroMesa: 5, pedidoId: 275, meseroNombre: 'Laura', estadoCocina: 'EN_PREPARACION' },
];

describe('Mesas ocupadas · una mesa con pedido abierto no recibe otro', () => {
  test('encuentra la mesa ocupada o dice que está libre', () => {
    expect(mesaOcupada(OCUPADAS, 5)?.pedidoId).toBe(275);
    expect(mesaOcupada(OCUPADAS, 6)).toBeNull();
    expect(mesaOcupada(OCUPADAS, null)).toBeNull();
    expect(mesaOcupada(OCUPADAS, 0)).toBeNull();
  });

  test('el mensaje dice quién atiende la mesa y cómo se libera, sin el número del pedido', () => {
    expect(mensajeMesaOcupada(OCUPADAS[1])).toBe('La mesa 5 está ocupada (la atiende Laura · En preparación). '
      + 'Se libera cuando su pedido se cobre o se cancele, o cuando se cambie a otra mesa.');
    expect(mensajeMesaOcupada(OCUPADAS[1]).includes('#')).toBe(false);
  });

  test('el número del pedido solo lo ve el administrador', () => {
    expect(nombrePedido(275, 5, true)).toBe('pedido #275 de la mesa 5');
    expect(nombrePedido(275, 5, false)).toBe('pedido de la mesa 5');
  });

  test('lista las mesas ocupadas en orden, con el mesero que la atiende', () => {
    expect(mesasConMesero(OCUPADAS).map((m) => m.texto)).toEqual(['Mesa 5 · Laura', 'Mesa 7 · Pedro']);
    expect(mesasConMesero([])).toEqual([]);
  });

  test('valida el cambio de mesa antes de enviarlo', () => {
    expect(errorCambioMesa('9', 5, OCUPADAS)).toBeNull();                    // libre
    expect(errorCambioMesa(' 9 ', 5, OCUPADAS)).toBeNull();
    expect(errorCambioMesa(null, 5, OCUPADAS)).toBeNull();                   // el mesero canceló el cuadro
    expect(errorCambioMesa('5', 5, OCUPADAS)).toBe('El pedido ya está en la mesa 5.');
    expect(errorCambioMesa('7', 5, OCUPADAS)).toContain('La mesa 7 está ocupada (la atiende Pedro');
    expect(errorCambioMesa('abc', 5, OCUPADAS)).toBe('Escriba un número de mesa entre 1 y 999.');
    expect(errorCambioMesa('0', 5, OCUPADAS)).toBe('Escriba un número de mesa entre 1 y 999.');
    expect(errorCambioMesa('2.5', 5, OCUPADAS)).toBe('Escriba un número de mesa entre 1 y 999.');
  });
});
