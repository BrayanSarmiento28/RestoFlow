// =====================================================================
// RestoFlow · Jest (pruebas unitarias de la lógica del frontend)
//
// Qué se prueba con Jest: la lógica de src/app/core que no depende de Angular: sesión, permisos de las rutas,
// token en las peticiones, mensajes de error, carrito de pedidos, cálculos de inventario, gráficas,
// código de barras (Open Food Facts), formatos y estados de cocina. No necesitan Angular ni navegador, por eso son rápidas.
// Desde el Sprint 6 también se prueban las pantallas, la campana, los servicios, los guards y el interceptor:
// cada pantalla se crea como una clase con servicios falsos (src/testing/angular.ts), sin navegador ni backend.
// Así la cobertura que ve SonarQube incluye todo el código del frontend, no solo la lógica de core/util.
//
//   npm run test:jest               ejecuta las pruebas (archivos *.test.ts)
//   npm run test:jest:cobertura     y genera el informe de cobertura en coverage/jest/
//
// El TypeScript se traduce con @swc/jest (muy rápido y compatible con TypeScript 6).
// =====================================================================

// Fechas y horas como en Colombia, en cualquier computador o en GitHub Actions
process.env.TZ = 'America/Bogota';

// TypeScript de la aplicación y de las pruebas: decoradores de Angular (@Component, @HostListener) como los compila
// Angular (experimentalDecorators y campos de clase sin "define", igual que tsconfig.json).
const swcTypeScript = ['@swc/jest', {
  jsc: {
    parser: { syntax: 'typescript', decorators: true },
    transform: { legacyDecorator: true, decoratorMetadata: false, useDefineForClassFields: false },
    target: 'es2022',
  },
  module: { type: 'commonjs' },
}];

// Las librerías de Angular se publican como módulos ES (.mjs); Jest las necesita en CommonJS.
const swcAngular = ['@swc/jest', {
  jsc: { parser: { syntax: 'ecmascript' }, target: 'es2022' },
  module: { type: 'commonjs' },
}];

/** @type {import('jest').Config} */
module.exports = {
  displayName: 'restoflow-frontend',
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  testMatch: ['**/*.test.ts'],
  setupFiles: ['<rootDir>/src/testing/preparar-jest.ts'],
  transform: {
    '^.+\\.ts$': swcTypeScript,
    '^.+\\.m?js$': swcAngular,
  },
  // Solo se traducen los paquetes de Angular; el resto de node_modules ya viene en CommonJS
  transformIgnorePatterns: ['/node_modules/(?!@angular/)'],
  moduleFileExtensions: ['ts', 'mjs', 'js', 'json'],
  // Todo el código de la aplicación cuenta (lo mismo que mide SonarQube), excepto lo que solo arranca Angular
  // y los modelos (interfaces de TypeScript, no tienen código que ejecutar).
  collectCoverageFrom: [
    'src/app/**/*.ts',
    '!src/**/*.test.ts',
    '!src/app/app-module.ts',
    '!src/app/app-routing-module.ts',
    '!src/app/app.ts',
    '!src/app/core/models/**',
  ],
  coverageDirectory: 'coverage/jest',
  coverageReporters: ['text', 'html', 'lcov'],
  // Si la cobertura baja de este nivel, las pruebas fallan (se revisa también en GitHub Actions)
  coverageThreshold: {
    global: { statements: 80, branches: 70, functions: 80, lines: 80 },
  },
  verbose: true,
};
