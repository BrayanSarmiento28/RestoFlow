import { SUGERENCIAS, agregarIntercambio, contador, puedePreguntar, textoOrigen } from './asistente-ia';

describe('RestoFlow AI con Gemini · lógica de pantalla', () => {
  test('dice de dónde salió cada texto', () => {
    expect(textoOrigen('GEMINI')).toBe('Redactado por Gemini');
    expect(textoOrigen('REGLAS')).toBe('Calculado con reglas (Gemini no disponible)');
    expect(textoOrigen(null)).toBe('');
  });

  test('solo deja enviar una pregunta válida y una a la vez', () => {
    expect(puedePreguntar('¿Qué compro?', false)).toBe(true);
    expect(puedePreguntar('   ', false)).toBe(false);
    expect(puedePreguntar('x'.repeat(501), false)).toBe(false);
    expect(puedePreguntar('¿Qué compro?', true)).toBe(false);
  });

  test('la conversación muestra lo más reciente arriba y como máximo 10', () => {
    let lista = [] as ReturnType<typeof agregarIntercambio>;
    for (let i = 1; i <= 12; i++) {
      lista = agregarIntercambio(lista, { pregunta: `P${i}`, respuesta: 'R', error: null, hora: '10:00' });
    }
    expect(lista).toHaveLength(10);
    expect(lista[0].pregunta).toBe('P12');
    expect(lista[9].pregunta).toBe('P3');
  });

  test('contador de caracteres y sugerencias', () => {
    expect(contador('  hola ')).toBe('4 / 500');
    expect(SUGERENCIAS).toHaveLength(3);
  });
});
