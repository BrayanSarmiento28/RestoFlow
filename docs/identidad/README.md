# Identidad visual de RestoFlow

**Símbolo:** un plato con tenedor y cuchillo; las dos ondas dentro del plato representan el *flujo* de los
pedidos entre el salón, la cocina y el inventario.

| Archivo | Uso |
|---|---|
| `logo.svg` | Símbolo en vector (aplicación, favicon, documentos) |
| `logo-256.png` | Símbolo en imagen (Word, diapositivas, redes) |
| `logo-horizontal.png` | Símbolo + nombre sobre fondo claro |
| `logo-horizontal-oscuro.png` | Símbolo + nombre sobre fondo oscuro |

En la aplicación el símbolo es el componente `app-logo` (`frontend/src/app/components/logo`), que toma el
color del texto; el favicon está en `frontend/public/favicon.svg` y `favicon.ico`.

## Colores

| Nombre | Hex | Uso |
|---|---|---|
| Azul RestoFlow | `#1D5CFF` | Color principal, botones, "Flow" sobre fondo claro |
| Azul claro | `#4F9DFF` | Degradado del símbolo, "Flow" sobre fondo oscuro |
| Azul noche | `#0B1530` | Menú lateral, textos principales |

## Tipografía

Nombre: **Poppins Bold** (en documentos). Aplicación: **DM Sans**.

## Reglas

- No deformar ni rotar el símbolo; dejar alrededor un espacio libre de al menos la mitad de su ancho.
- Sobre fondos oscuros usar `logo-horizontal-oscuro.png`.
