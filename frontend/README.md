# RestoFlow · Frontend

Responsable: **Juan Sebastián Patiño Ocampo**

- Angular (estructura por módulos: `app-module.ts` y `app-routing-module.ts`)
- Diseños de interfaz en Figma
- Consume el API REST del backend

## Cómo ejecutarlo

Requisitos: Node.js LTS y Angular CLI (`npm install -g @angular/cli`). Con el backend encendido:

```
cd frontend
npm install        # solo la primera vez
npm start          # desarrollo → http://localhost:4200
```

Usuario inicial: `admin@restoflow.com` / `Admin2026*`.

## Entornos

| Comando | Entorno | Archivo de configuración | API |
|---|---|---|---|
| `npm start` | Desarrollo | `src/environments/environment.ts` | `http://localhost:8080/api` |
| `npm run start:preprod` · `npm run build:preprod` | Pre-producción | `src/environments/environment.preprod.ts` | servidor de pre-producción |
| `npm run build:prod` | Producción | `src/environments/environment.prod.ts` | `/api` (mismo dominio) |

En desarrollo y pre-producción el menú lateral muestra una etiqueta con el entorno activo.

## Estructura

```
src/app/
├── core/          models, services (AuthService, UsuarioService), interceptors (JWT) y guards (sesión y rol)
├── components/    componentes reutilizables (panel de marca)
├── layout/        estructura con menú lateral
└── pages/         login, recuperar-contrasena, inicio (dashboard), usuarios (solo ADMIN)
```
