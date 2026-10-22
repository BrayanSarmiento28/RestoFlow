// =====================================================================
// RestoFlow · Jest (pruebas unitarias de la lógica del frontend)
//
// Qué se prueba con Jest: la lógica de src/app/core que no depende de Angular: sesión, permisos de las rutas,
// token en las peticiones, mensajes de error, carrito de pedidos, cálculos de inventario, gráficas,
// código de barras (Open Food Facts), formatos y estados de cocina. No necesitan Angular ni navegador, por eso son rápidas.
// Las pruebas de componentes de Angular siguen con "ng test" (archivos *.spec.ts).
//
//   npm run test:jest               ejecuta las pruebas (archivos *.test.ts)
//   npm run test:jest:cobertura     y genera el informe de cobertura en coverage/jest/
//
// El TypeScript se traduce con @swc/jest (muy rápido y compatible con TypeScript 6).
// =====================================================================

// Fechas y horas como en Colombia, en cualquier computador o en GitHub Actions
process.env.TZ = 'America/Bogota';

/** @type {import('jest').Config} */
module.exports = {
  displayName: 'restoflow-frontend',
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  testMatch: ['**/*.test.ts'],
  transform: {
    '^.+\\.ts$': ['@swc/jest', {
      jsc: { parser: { syntax: 'typescript' }, target: 'es2022' },
      module: { type: 'commonjs' },
    }],
  },
  moduleFileExtensions: ['ts', 'js', 'json'],
  collectCoverageFrom: [
    'src/app/core/util/**/*.ts',
    'src/app/core/entities/pedido.ts',
    'src/app/core/services/errores.ts',
    '!src/**/*.test.ts',
  ],
  coverageDirectory: 'coverage/jest',
  coverageReporters: ['text', 'html', 'lcov'],
  // Si la cobertura de la lógica baja de este nivel, la prueba falla (se revisa también en GitHub Actions)
  coverageThreshold: {
    global: { statements: 80, branches: 60, functions: 80, lines: 80 },
  },
  verbose: true,
};
