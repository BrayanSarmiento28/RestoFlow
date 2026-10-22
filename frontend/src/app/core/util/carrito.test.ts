// Pruebas unitarias con Jest · RF-02 · pedido que arma el mesero
import type { Producto } from '../entities/producto';
import {
  ItemCarrito, MAXIMO_POR_PRODUCTO, aPedidoRequest, agregarProducto, cambiarCantidad, cambiarNota, carritoDesdePedido,
  categoriasDe, filtrarProductos, filtroDePedidos, quitarItem, totalCarrito,
} from './carrito';

function producto(id: number, nombre: string, precio: number, categoriaNombre = 'Platos fuertes'): Producto {
  return { id, nombre, descripcion: null, precio, categoriaId: 1, categoriaNombre, activo: true };
}

const bandeja = producto(1, 'Bandeja paisa', 32000);
const limonada = producto(2, 'Limonada de coco', 9000, 'Bebidas');
const ajiaco = producto(3, 'Ajiaco', 28000, 'Sopas');

describe('agregarProducto', () => {
  it('agrega un producto nuevo con cantidad 1 y sin nota', () => {
    expect(agregarProducto([], bandeja)).toEqual([{ producto: bandeja, cantidad: 1, nota: '' }]);
  });

  it('si el producto ya está, suma 1 en vez de repetir la línea', () => {
    const carrito = agregarProducto(agregarProducto([], bandeja), bandeja);
    expect(carrito).toHaveLength(1);
    expect(carrito[0].cantidad).toBe(2);
  });

  it('no modifica el carrito anterior (devuelve uno nuevo, como lo necesitan las signals)', () => {
    const antes: ItemCarrito[] = [];
    agregarProducto(antes, bandeja);
    expect(antes).toEqual([]);
  });
});

describe('cambiarCantidad y quitarItem', () => {
  it('suma y resta unidades', () => {
    const carrito = [{ producto: bandeja, cantidad: 2, nota: '' }];
    expect(cambiarCantidad(carrito, carrito[0], 3)[0].cantidad).toBe(5);
    expect(cambiarCantidad(carrito, carrito[0], -1)[0].cantidad).toBe(1);
  });

  it('al llegar a 0 quita la línea', () => {
    const carrito = [{ producto: bandeja, cantidad: 1, nota: '' }];
    expect(cambiarCantidad(carrito, carrito[0], -1)).toEqual([]);
  });

  it('nunca pasa de 99 unidades por producto', () => {
    const carrito = [{ producto: bandeja, cantidad: 98, nota: '' }];
    expect(cambiarCantidad(carrito, carrito[0], 5)[0].cantidad).toBe(MAXIMO_POR_PRODUCTO);
  });

  it('quitar deja las demás líneas', () => {
    const carrito = [{ producto: bandeja, cantidad: 1, nota: '' }, { producto: limonada, cantidad: 2, nota: '' }];
    expect(quitarItem(carrito, carrito[0]).map((i) => i.producto.nombre)).toEqual(['Limonada de coco']);
  });

  it('cambia la nota solo de esa línea', () => {
    const carrito = [{ producto: bandeja, cantidad: 1, nota: '' }, { producto: limonada, cantidad: 1, nota: '' }];
    const nuevo = cambiarNota(carrito, carrito[1], 'Sin hielo');
    expect(nuevo.map((i) => i.nota)).toEqual(['', 'Sin hielo']);
  });
});

describe('totalCarrito', () => {
  it('suma precio por cantidad de cada línea', () => {
    expect(totalCarrito([{ producto: bandeja, cantidad: 2, nota: '' }, { producto: limonada, cantidad: 3, nota: '' }]))
      .toBe(32000 * 2 + 9000 * 3);
    expect(totalCarrito([])).toBe(0);
  });
});

describe('aPedidoRequest', () => {
  it('arma lo que espera el API; los textos vacíos viajan como null', () => {
    const carrito = [{ producto: bandeja, cantidad: 2, nota: '  sin fríjoles ' }, { producto: limonada, cantidad: 1, nota: '   ' }];
    expect(aPedidoRequest('4', '   ', carrito)).toEqual({
      numeroMesa: 4,
      observaciones: null,
      lineas: [
        { productoId: 1, cantidad: 2, nota: 'sin fríjoles' },
        { productoId: 2, cantidad: 1, nota: null },
      ],
    });
  });
});

describe('carritoDesdePedido', () => {
  const pedido = {
    lineas: [
      { id: 10, productoId: 1, productoNombre: 'Bandeja paisa', cantidad: 2, precioUnitario: 32000, subtotal: 64000, nota: null },
      { id: 11, productoId: 2, productoNombre: 'Limonada de coco', cantidad: 1, precioUnitario: 9000, subtotal: 9000, nota: 'Sin hielo' },
    ],
  };

  it('vuelve a armar el carrito para editar el pedido', () => {
    const { items, faltante } = carritoDesdePedido(pedido, [bandeja, limonada]);
    expect(faltante).toBeNull();
    expect(items.map((i) => [i.producto.id, i.cantidad, i.nota])).toEqual([[1, 2, ''], [2, 1, 'Sin hielo']]);
  });

  it('si un producto ya no está en el menú, indica cuál (no se puede editar)', () => {
    expect(carritoDesdePedido(pedido, [bandeja])).toEqual({ items: [], faltante: 'Limonada de coco' });
  });
});

describe('filtrarProductos y categoriasDe', () => {
  const menu = [bandeja, limonada, ajiaco];

  it('"Todas" y cada categoría una sola vez', () => {
    expect(categoriasDe([...menu, producto(4, 'Mojarra', 35000)])).toEqual(['Todas', 'Platos fuertes', 'Bebidas', 'Sopas']);
  });

  it('filtra por categoría y por texto sin importar mayúsculas ni espacios', () => {
    expect(filtrarProductos(menu, 'Todas', '').map((p) => p.id)).toEqual([1, 2, 3]);
    expect(filtrarProductos(menu, 'Bebidas', '').map((p) => p.id)).toEqual([2]);
    expect(filtrarProductos(menu, 'Todas', '  AJI ').map((p) => p.id)).toEqual([3]);
    expect(filtrarProductos(menu, 'Sopas', 'bandeja')).toEqual([]);
  });
});

describe('filtroDePedidos', () => {
  it('traduce cada opción de la pantalla a los parámetros del API', () => {
    expect(filtroDePedidos('ACTIVOS', null)).toEqual({ mesa: null, estadoPedido: 'ABIERTO' });
    expect(filtroDePedidos('PAGADO', 5)).toEqual({ mesa: 5, estadoPedido: 'PAGADO' });
    expect(filtroDePedidos('CANCELADO', null)).toEqual({ mesa: null, estadoPedido: 'CANCELADO' });
    expect(filtroDePedidos('EN_PREPARACION', null)).toEqual({ mesa: null, estadoCocina: 'EN_PREPARACION' });
    expect(filtroDePedidos('TODOS', 2)).toEqual({ mesa: 2 });
  });
});
