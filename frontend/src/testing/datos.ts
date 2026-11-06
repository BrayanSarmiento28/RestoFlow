// Datos de ejemplo para las pruebas: cada función arma un objeto completo y deja cambiar lo necesario.
import { Alerta, Ingrediente, MovimientoInventario, Pedido, Producto } from '../app/core/entities';
import { Aviso } from '../app/core/models/avisos.models';
import { ResumenPanel } from '../app/core/models/inventario.models';
import { AnalisisRestoFlowAI, Recomendacion } from '../app/core/models/restoflow-ai.models';

export function producto(cambios: Partial<Producto> = {}): Producto {
  return { id: 1, nombre: 'Bandeja paisa', descripcion: null, precio: 32000, categoriaId: 1,
    categoriaNombre: 'Platos fuertes', activo: true, ...cambios };
}

export function pedido(cambios: Partial<Pedido> = {}): Pedido {
  return {
    id: 40, numeroMesa: 3, meseroId: 7, meseroNombre: 'Laura Gómez', observaciones: null,
    lineas: [{ id: 1, productoId: 1, productoNombre: 'Bandeja paisa', cantidad: 2, precioUnitario: 32000, subtotal: 64000, nota: null }],
    estadoCocina: 'PENDIENTE', estadoPedido: 'ABIERTO', total: 64000, fechaCreacion: '2026-11-05T12:00:00',
    fechaInicioPreparacion: null, fechaListo: null, fechaEntrega: null, fechaCierre: null, numeroDelDia: 4, metodoPago: null,
    fechaAdicion: null,
    ...cambios,
  };
}

export function ingrediente(cambios: Partial<Ingrediente> = {}): Ingrediente {
  return { id: 1, nombre: 'Arroz', unidad: 'GRAMO', stockActual: 5000, stockMinimo: 1000, activo: true,
    enMinimo: false, agotado: false, ...cambios };
}

export function movimiento(cambios: Partial<MovimientoInventario> = {}): MovimientoInventario {
  return { id: 1, ingredienteId: 1, ingredienteNombre: 'Arroz', unidad: 'GRAMO', tipo: 'ENTRADA', cantidad: 500,
    motivo: 'Compra', usuarioId: 7, usuarioNombre: 'Laura Gómez', pedidoId: null, pedidoNumeroDelDia: null,
    pedidoMesa: null, fecha: '2026-11-05T09:00:00', ...cambios };
}

export function alerta(cambios: Partial<Alerta> = {}): Alerta {
  return { id: 1, ingredienteId: 1, ingredienteNombre: 'Arroz', mensaje: 'Arroz en su mínimo', prioridad: 'ALTA',
    atendida: false, fechaCreacion: '2026-11-05T09:00:00', fechaAtencion: null, ...cambios };
}

export function aviso(cambios: Partial<Aviso> = {}): Aviso {
  return { clave: 'SIN_COBRAR:40', tipo: 'SIN_COBRAR', nivel: 'URGENTE', titulo: 'Mesa 3 · sin cobrar',
    mensaje: 'Se entregó hace 40 min', enlace: '/pedidos', desde: '2026-11-05T12:00:00', ...cambios };
}

export function recomendacion(cambios: Partial<Recomendacion> = {}): Recomendacion {
  return { tipo: 'COMPRA', prioridad: 'ALTA', titulo: 'Comprar arroz', detalle: 'Alcanza para 1 día',
    ingredienteId: 1, productoId: null, ...cambios };
}

export function resumenPanel(cambios: Partial<ResumenPanel> = {}): ResumenPanel {
  return {
    dia: '2026-11-05', ventas: 150000, pedidosPagados: 5, ticketPromedio: 30000, ventasDiaAnterior: 100000,
    pedidosAbiertos: 2, pedidosEnCocina: 1, pedidosCancelados: 0, ventasPorHora: [{ hora: 12, total: 150000, pedidos: 5 }],
    masVendidos: [], insumosEnMinimo: [], alertasPendientes: 0, pedidosRegistrados: 7, porMesero: [], porMetodoPago: [],
    ...cambios,
  };
}

export function analisis(cambios: Partial<AnalisisRestoFlowAI> = {}): AnalisisRestoFlowAI {
  return {
    generadoEn: '2026-11-05T08:00:00',
    ventas: {
      diasConVentas: 20, datosSuficientes: true, ventasSemana: 900000, tendenciaVentas: 12, diaMasFuerte: 'SATURDAY',
      horaPico: 13,
      productos: [{ productoId: 1, nombre: 'Bandeja paisa', unidadesSemana: 30, unidadesSemanaAnterior: 25, tendenciaPorcentaje: 20 }],
      ventasPorDia: [{ dia: 'FRIDAY', promedio: 200000 }, { dia: 'SATURDAY', promedio: 300000 }],
      prediccion: null,
    },
    insumos: [{ ingredienteId: 1, nombre: 'Arroz', unidad: 'GRAMO', stockActual: 900, stockMinimo: 1000, consumoDiario: 800,
      consumoSemana: 5600, tendenciaPorcentaje: 5, diasCobertura: 1.1, prioridad: 'ALTA', cantidadSugerida: 5000 }],
    recomendaciones: [recomendacion(), recomendacion({ prioridad: 'MEDIA', tipo: 'TENDENCIA', titulo: 'Sube la bandeja' }),
      recomendacion({ prioridad: 'BAJA', tipo: 'INFORMACION', titulo: 'Sábado fuerte' })],
    ...cambios,
  };
}
