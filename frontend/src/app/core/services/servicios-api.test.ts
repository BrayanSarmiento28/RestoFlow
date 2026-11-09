// Pruebas con Jest · servicios que hablan con el API: cada método llama a la dirección y con los datos correctos.
// HttpClient se reemplaza por uno falso que guarda lo que se le pide (no hay red ni backend).
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injector, Type, runInInjectionContext } from '@angular/core';
import { of } from 'rxjs';
import { AvisosService } from './avisos.service';
import { CatalogoService } from './catalogo.service';
import { CocinaService } from './cocina.service';
import { InventarioService } from './inventario.service';
import { PanelService } from './panel.service';
import { PedidoService } from './pedido.service';
import { RestoFlowAIService } from './restoflow-ai.service';
import { UsuarioService } from './usuario.service';
import { environment } from '../../../environments/environment';

const API = environment.apiUrl;

function httpFalso() {
  const responder = jest.fn(() => of('respuesta'));
  return { get: responder, post: responder, put: responder, patch: responder, llamadas: responder.mock.calls };
}

function crearServicio<T>(Tipo: Type<T>) {
  const http = httpFalso();
  const injector = Injector.create({ providers: [{ provide: HttpClient, useValue: http }] });
  return { servicio: runInInjectionContext(injector, () => new Tipo()), http };
}

/** URL y parámetros (como texto) de la última llamada. */
function ultima(http: ReturnType<typeof httpFalso>) {
  const [url, segundo, tercero] = http.llamadas[http.llamadas.length - 1] as unknown[];
  const opciones = (tercero ?? segundo) as { params?: HttpParams } | undefined;
  return { url, cuerpo: segundo, params: opciones?.params?.toString() ?? '' };
}

describe('Servicios del API', () => {
  it('PedidoService: consultar con filtros, registrar, modificar, cancelar, cobrar, mesas y cambio de mesa', () => {
    const { servicio, http } = crearServicio(PedidoService);
    servicio.consultar({ mesa: 5, estadoCocina: 'LISTO', estadoPedido: 'ABIERTO' }).subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/pedidos`, params: 'mesa=5&estadoCocina=LISTO&estadoPedido=ABIERTO' });
    servicio.consultar().subscribe();
    expect(ultima(http).params).toBe('');
    servicio.registrar({ numeroMesa: 3, lineas: [] }).subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/pedidos`, cuerpo: { numeroMesa: 3, lineas: [] } });
    servicio.modificar(4, { numeroMesa: 3, lineas: [] }).subscribe();
    expect(ultima(http).url).toBe(`${API}/pedidos/4`);
    servicio.cancelar(4).subscribe();
    expect(ultima(http).url).toBe(`${API}/pedidos/4/cancelar`);
    servicio.pagar(4, 'EFECTIVO').subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/pedidos/4/pagar`, cuerpo: { metodoPago: 'EFECTIVO' } });
    servicio.mesasOcupadas().subscribe();
    expect(ultima(http).url).toBe(`${API}/pedidos/mesas-ocupadas`);
    servicio.agregarAdicion(4, [{ productoId: 2, cantidad: 1, nota: null }]).subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/pedidos/4/adiciones`, cuerpo: { lineas: [{ productoId: 2, cantidad: 1 }] } });
    servicio.cambiarMesa(4, 9).subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/pedidos/4/mesa`, cuerpo: { numeroMesa: 9 } });
  });

  it('CatalogoService: categorías y productos', () => {
    const { servicio, http } = crearServicio(CatalogoService);
    servicio.categorias().subscribe();
    expect(ultima(http).url).toBe(`${API}/categorias`);
    servicio.crearCategoria('Postres').subscribe();
    expect(ultima(http).cuerpo).toEqual({ nombre: 'Postres' });
    servicio.productos(true).subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/productos`, params: 'soloActivos=true' });
    const datos = { nombre: 'Ajiaco', descripcion: null, precio: 1, categoriaId: 1 };
    servicio.crearProducto(datos).subscribe();
    expect(ultima(http).url).toBe(`${API}/productos`);
    servicio.editarProducto(2, datos).subscribe();
    expect(ultima(http).url).toBe(`${API}/productos/2`);
    servicio.cambiarDisponibilidad(2, false).subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/productos/2/disponibilidad`, cuerpo: { activo: false } });
  });

  it('InventarioService: insumos, movimientos (solo los filtros con valor), alertas, receta y código de barras', () => {
    const { servicio, http } = crearServicio(InventarioService);
    servicio.ingredientes().subscribe();
    expect(ultima(http).params).toBe('soloActivos=false');
    const insumo = { nombre: 'Arroz', unidad: 'GRAMO' as const, stockMinimo: 1 };
    servicio.crear(insumo).subscribe();
    expect(ultima(http).url).toBe(`${API}/inventario/ingredientes`);
    servicio.editar(1, insumo).subscribe();
    expect(ultima(http).url).toBe(`${API}/inventario/ingredientes/1`);
    servicio.cambiarEstado(1, true).subscribe();
    expect(ultima(http).url).toBe(`${API}/inventario/ingredientes/1/estado`);
    servicio.entrada(1, { cantidad: 5 }).subscribe();
    expect(ultima(http).url).toBe(`${API}/inventario/ingredientes/1/entradas`);
    servicio.salida(1, { cantidad: 5 }).subscribe();
    expect(ultima(http).url).toBe(`${API}/inventario/ingredientes/1/salidas`);
    servicio.ajuste(1, { stockContado: 5 }).subscribe();
    expect(ultima(http).url).toBe(`${API}/inventario/ingredientes/1/ajustes`);
    servicio.movimientos({ ingredienteId: 1, tipo: undefined, desde: '2026-11-01', hasta: '' }).subscribe();
    expect(ultima(http).params).toBe('ingredienteId=1&desde=2026-11-01');
    servicio.movimientos().subscribe();
    expect(ultima(http).params).toBe('');
    servicio.alertas().subscribe();
    expect(ultima(http).params).toBe('soloPendientes=true');
    servicio.atender(3).subscribe();
    expect(ultima(http).url).toBe(`${API}/alertas/3/atender`);
    servicio.receta(2).subscribe();
    expect(ultima(http).url).toBe(`${API}/productos/2/receta`);
    servicio.definirReceta(2, [{ ingredienteId: 1, cantidad: 3 }]).subscribe();
    expect(ultima(http).cuerpo).toEqual({ lineas: [{ ingredienteId: 1, cantidad: 3 }] });
    servicio.buscarPorCodigoBarras('77 02').subscribe();
    expect(ultima(http).url).toBe(`${API}/inventario/catalogo-externo/77%2002`);
  });

  it('PanelService: resumen (hoy o una fecha), ventas e historial de pedidos', () => {
    const { servicio, http } = crearServicio(PanelService);
    servicio.resumen().subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/panel/resumen`, params: '' });
    servicio.resumen('2026-11-04').subscribe();
    expect(ultima(http).params).toBe('fecha=2026-11-04');
    servicio.ventas('2026-11-01', '2026-11-05').subscribe();
    expect(ultima(http)).toMatchObject({ url: `${API}/historial/ventas`, params: 'desde=2026-11-01&hasta=2026-11-05' });
    servicio.pedidos('2026-11-01', '2026-11-05', 'PAGADO').subscribe();
    expect(ultima(http).params).toBe('desde=2026-11-01&hasta=2026-11-05&estado=PAGADO');
    servicio.pedidos('2026-11-01', '2026-11-05').subscribe();
    expect(ultima(http).params).toBe('desde=2026-11-01&hasta=2026-11-05');
  });

  it('CocinaService, UsuarioService, AvisosService y RestoFlowAIService', () => {
    const cocina = crearServicio(CocinaService);
    cocina.servicio.comandas().subscribe();
    expect(ultima(cocina.http).url).toBe(`${API}/cocina/comandas`);
    cocina.servicio.cambiarEstado(4, 'LISTO').subscribe();
    expect(ultima(cocina.http)).toMatchObject({ url: `${API}/cocina/pedidos/4/estado`, cuerpo: { estado: 'LISTO' } });

    const usuarios = crearServicio(UsuarioService);
    usuarios.servicio.listar().subscribe();
    expect(ultima(usuarios.http).url).toBe(`${API}/usuarios`);
    usuarios.servicio.crear({ nombre: 'Pedro', email: 'p@r.com', password: 'Clave2026', rol: 'COCINA' }).subscribe();
    expect(ultima(usuarios.http).cuerpo).toMatchObject({ rol: 'COCINA' });
    usuarios.servicio.cambiarEstado(8, false).subscribe();
    expect(ultima(usuarios.http)).toMatchObject({ url: `${API}/usuarios/8/estado`, cuerpo: { activa: false } });

    const avisos = crearServicio(AvisosService);
    avisos.servicio.consultar().subscribe();
    expect(ultima(avisos.http).url).toBe(`${API}/avisos`);
    avisos.servicio.resumen().subscribe();
    expect(ultima(avisos.http).url).toBe(`${API}/avisos/resumen`);

    const ia = crearServicio(RestoFlowAIService);
    ia.servicio.analisis().subscribe();
    expect(ultima(ia.http).url).toBe(`${API}/restoflow-ai/analisis`);
    ia.servicio.generarAlertas().subscribe();
    expect(ultima(ia.http).url).toBe(`${API}/restoflow-ai/alertas`);
    ia.servicio.recomendacionesIA().subscribe();
    expect(ultima(ia.http).url).toBe(`${API}/restoflow-ai/recomendaciones-ia`);
    ia.servicio.preguntar('¿Qué compro?').subscribe();
    expect(ultima(ia.http).cuerpo).toEqual({ pregunta: '¿Qué compro?' });
  });
});
