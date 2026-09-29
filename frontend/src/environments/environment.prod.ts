// PRODUCCIÓN: se usa con "npm run build:prod" (lo que publica el pipeline desde la rama main).
// "/api" significa que el frontend y el backend se publican bajo el mismo dominio.
export const environment = {
  nombre: 'produccion',
  produccion: true,
  apiUrl: '/api',
};
