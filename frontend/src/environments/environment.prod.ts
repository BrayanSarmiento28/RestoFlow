// PRODUCCIÓN: se usa con "npm run build:prod" (lo que publica el pipeline desde la rama main).
// "/api" significa que el frontend y el backend se publican bajo el mismo dominio.
export const environment = {
  nombre: 'produccion',
  produccion: true,
  apiUrl: '/api',
  version: '0.2.0',
  // Cada cuánto se consultan de nuevo las comandas de cocina y la lista de pedidos (milisegundos).
  // 2,5 s: un cambio de estado se ve en las otras pantallas en menos de 3 segundos (RES-28, docs/pruebas/rendimiento.md).
  refrescoCocinaMs: 2500,
  refrescoPedidosMs: 2500,
};
