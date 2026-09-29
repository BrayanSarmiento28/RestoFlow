// PRE-PRODUCCIÓN: se usa con "npm run start:preprod" o "npm run build:preprod".
// Cuando exista el servidor de pre-producción, cambiar apiUrl por su dirección.
export const environment = {
  nombre: 'pre-produccion',
  produccion: false,
  apiUrl: 'http://localhost:8080/api',
};
