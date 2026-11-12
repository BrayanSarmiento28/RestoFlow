// Pruebas con Jest · símbolo de RestoFlow.
import { Logo } from './logo';
import { crear } from '../../../testing/angular';

describe('Logo', () => {
  it('por defecto mide 24 px', () => {
    const { componente, destruir } = crear(Logo);
    expect(componente.tamano()).toBe(24);
    destruir();
  });
});
