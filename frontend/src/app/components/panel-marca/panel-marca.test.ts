// Pruebas con Jest · panel de marca del login (solo presentación).
import { PanelMarca } from './panel-marca';
import { crear } from '../../../testing/angular';

describe('Panel de marca', () => {
  it('se crea sin depender de ningún servicio', () => {
    const { componente, destruir } = crear(PanelMarca);
    expect(componente).toBeInstanceOf(PanelMarca);
    destruir();
  });
});
