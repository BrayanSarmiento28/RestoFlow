// Pruebas con Jest · pantalla de Productos (menú, categorías y receta de cada producto).
import { Productos } from './productos';
import { CatalogoService } from '../../core/services/catalogo.service';
import { InventarioService } from '../../core/services/inventario.service';
import { Montaje, crear, falla, ok, simularNavegador } from '../../../testing/angular';
import { ingrediente, producto } from '../../../testing/datos';

const bandeja = producto({ id: 1, nombre: 'Bandeja paisa', descripcion: 'Con chicharrón', precio: 32000, categoriaId: 2 });
const jugo = producto({ id: 2, nombre: 'Jugo natural', precio: 7000, activo: false });

function apis() {
  return {
    catalogo: {
      categorias: jest.fn(() => ok([{ id: 2, nombre: 'Platos fuertes', activa: true }])),
      productos: jest.fn(() => ok([bandeja, jugo])),
      crearProducto: jest.fn(() => ok(producto({ id: 3, nombre: 'Ajiaco' }))),
      editarProducto: jest.fn(() => ok({ ...bandeja, precio: 34000 })),
      cambiarDisponibilidad: jest.fn((id: number, activo: boolean) => ok({ ...bandeja, activo })),
      crearCategoria: jest.fn(() => ok({ id: 5, nombre: 'Postres', activa: true })),
    },
    inventario: {
      ingredientes: jest.fn(() => ok([ingrediente({ id: 1, nombre: 'Arroz' }), ingrediente({ id: 2, nombre: 'Huevo', unidad: 'UNIDAD' })])),
      receta: jest.fn(() => ok([{ ingredienteId: 1, ingredienteNombre: 'Arroz', unidad: 'GRAMO' as const, cantidad: 200 }])),
      definirReceta: jest.fn((id: number, lineas: unknown[]) => ok(lineas)),
    },
  };
}

describe('Pantalla de Productos', () => {
  let montaje: Montaje<Productos>;
  let api: ReturnType<typeof apis>;
  let navegador: ReturnType<typeof simularNavegador>;

  function abrir() {
    navegador = simularNavegador();
    api = apis();
    montaje = crear(Productos, [
      { provide: CatalogoService, useValue: api.catalogo },
      { provide: InventarioService, useValue: api.inventario },
    ]);
    montaje.componente.ngOnInit();
    return montaje.componente;
  }

  afterEach(() => {
    montaje.destruir();
    navegador.restaurar();
  });

  it('carga las categorías y todos los productos (también los desactivados)', () => {
    const c = abrir();
    expect(c['categorias']()).toHaveLength(1);
    expect(c['productos']()).toHaveLength(2);
    expect(api.catalogo.productos).toHaveBeenCalledWith();
  });

  it('si el API falla al cargar, muestra el error', () => {
    navegador = simularNavegador();
    api = apis();
    api.catalogo.categorias.mockReturnValue(falla(0));
    api.catalogo.productos.mockReturnValue(falla(0));
    montaje = crear(Productos, [
      { provide: CatalogoService, useValue: api.catalogo },
      { provide: InventarioService, useValue: api.inventario },
    ]);
    montaje.componente.ngOnInit();
    expect(montaje.componente['error']()).toContain('No se pudo conectar');
  });

  it('valida el formulario y crea el producto nuevo', () => {
    const c = abrir();
    c['guardar']();
    expect(c['invalido']('nombre')).toBe(true);
    expect(api.catalogo.crearProducto).not.toHaveBeenCalled();

    c['form'].patchValue({ nombre: ' Ajiaco ', descripcion: '  ', precio: 25000, categoriaId: 2 });
    c['guardar']();
    expect(api.catalogo.crearProducto).toHaveBeenCalledWith({ nombre: 'Ajiaco', descripcion: null, precio: 25000, categoriaId: 2 });
    expect(c['exito']()).toBe('Producto Ajiaco creado.');
    expect(c['form'].controls.nombre.value).toBe('');
  });

  it('edita el precio de un producto existente', () => {
    const c = abrir();
    c['editar'](bandeja);
    expect(c['editandoId']()).toBe(1);
    expect(c['form'].controls.descripcion.value).toBe('Con chicharrón');
    expect(navegador.ventana.scrollTo).toHaveBeenCalled();
    c['form'].patchValue({ precio: 34000 });
    c['guardar']();
    expect(api.catalogo.editarProducto).toHaveBeenCalledWith(1, {
      nombre: 'Bandeja paisa', descripcion: 'Con chicharrón', precio: 34000, categoriaId: 2,
    });
    expect(c['exito']()).toBe('Producto Bandeja paisa actualizado.');
    expect(c['editandoId']()).toBeNull();
  });

  it('si guardar falla, muestra el error del API', () => {
    const c = abrir();
    api.catalogo.crearProducto.mockReturnValue(falla(400, 'El precio debe ser mayor que cero'));
    c['form'].patchValue({ nombre: 'Ajiaco', precio: 1, categoriaId: 2 });
    c['guardar']();
    expect(c['error']()).toBe('El precio debe ser mayor que cero');
    expect(c['guardando']()).toBe(false);
  });

  it('activa y desactiva productos para la venta', () => {
    const c = abrir();
    c['alternar'](bandeja);
    expect(api.catalogo.cambiarDisponibilidad).toHaveBeenCalledWith(1, false);
    expect(c['exito']()).toBe('Bandeja paisa desactivado.');
    c['alternar'](jugo);
    expect(c['exito']()).toBe('Bandeja paisa disponible para la venta.');
    api.catalogo.cambiarDisponibilidad.mockReturnValue(falla(403));
    c['alternar'](bandeja);
    expect(c['error']()).toBe('Tu rol no tiene permiso para esta acción.');
  });

  it('crea una categoría y la deja elegida en el formulario', () => {
    const c = abrir();
    c['crearCategoria']();
    expect(api.catalogo.crearCategoria).not.toHaveBeenCalled();
    c['nuevaCategoria'].setValue(' Postres ');
    c['crearCategoria']();
    expect(api.catalogo.crearCategoria).toHaveBeenCalledWith('Postres');
    expect(c['form'].controls.categoriaId.value).toBe(5);
    expect(c['exito']()).toBe('Categoría Postres creada.');
    api.catalogo.crearCategoria.mockReturnValue(falla(409, 'Ya existe'));
    c['nuevaCategoria'].setValue('Postres');
    c['crearCategoria']();
    expect(c['error']()).toBe('Ya existe');
  });

  it('abre la receta: carga los insumos una sola vez y las líneas guardadas', () => {
    const c = abrir();
    c['abrirReceta'](bandeja);
    c['abrirReceta'](bandeja);
    expect(api.inventario.ingredientes).toHaveBeenCalledTimes(1);
    expect(c['lineas']()).toEqual([{ ingredienteId: 1, cantidad: 200 }]);
    expect(c['unidadDe'](1)).toBe('g');
    expect(c['unidadDe'](null)).toBe('');
  });

  it('edita las líneas de la receta y la guarda', () => {
    const c = abrir();
    c['abrirReceta'](bandeja);
    c['agregarLinea']();
    c['cambiarLinea'](1, 'ingredienteId', '2');
    c['cambiarLinea'](1, 'cantidad', '');
    c['guardarReceta']();
    expect(c['error']()).toBe('Completa cada línea: elige el insumo y una cantidad mayor que cero.');
    c['cambiarLinea'](1, 'cantidad', '1');
    c['guardarReceta']();
    expect(api.inventario.definirReceta).toHaveBeenCalledWith(1, [{ ingredienteId: 1, cantidad: 200 }, { ingredienteId: 2, cantidad: 1 }]);
    expect(c['exito']()).toBe('Receta de Bandeja paisa guardada: 2 insumos se descontarán en cada venta.');
    expect(c['recetaDe']()).toBeNull();
  });

  it('una receta vacía avisa que las ventas no descuentan inventario', () => {
    const c = abrir();
    c['abrirReceta'](bandeja);
    c['quitarLinea'](0);
    c['guardarReceta']();
    expect(c['exito']()).toBe('Bandeja paisa quedó sin receta: sus ventas no descuentan inventario.');
  });

  it('sin producto abierto no guarda receta; si el API falla, lo muestra', () => {
    const c = abrir();
    c['guardarReceta']();
    expect(api.inventario.definirReceta).not.toHaveBeenCalled();
    api.inventario.definirReceta.mockReturnValue(falla(400, 'Insumo inactivo'));
    c['abrirReceta'](bandeja);
    c['guardarReceta']();
    expect(c['error']()).toBe('Insumo inactivo');
    expect(c['guardando']()).toBe(false);
  });

  it('si no carga la receta o los insumos, muestra el error', () => {
    const c = abrir();
    api.inventario.ingredientes.mockReturnValue(falla(0));
    api.inventario.receta.mockReturnValue(falla(404, 'Producto no encontrado'));
    c['abrirReceta'](bandeja);
    expect(c['error']()).toBe('Producto no encontrado');
  });
});
