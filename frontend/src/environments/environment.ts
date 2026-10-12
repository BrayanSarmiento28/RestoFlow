// DESARROLLO: se usa con "ng serve" (npm start). El backend corre en tu computador.
export const environment = {
  nombre: 'desarrollo',
  produccion: false,
  apiUrl: 'http://localhost:8080/api',
  version: '0.2.0',
  // Cada cuánto se consultan de nuevo las comandas de cocina y la lista de pedidos (milisegundos)
  refrescoCocinaMs: 5000,
  refrescoPedidosMs: 10000,
};
