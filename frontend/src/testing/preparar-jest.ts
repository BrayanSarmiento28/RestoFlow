// Se ejecuta antes de cada archivo de prueba de Jest (setupFiles en jest.config.js).
// Las librerías de Angular vienen compiladas a medias ("partial compilation"); el compilador JIT termina
// de prepararlas al cargarlas. Sin esta línea, crear una pantalla en una prueba falla con
// "JIT compilation failed for injectable PlatformLocation".
import '@angular/compiler';
