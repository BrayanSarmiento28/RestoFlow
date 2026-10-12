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
| `npm run start:prod` · `npm run build:prod` | Producción | `src/environments/environment.prod.ts` | `/api` (mismo dominio) |

En desarrollo y pre-producción el menú lateral muestra una etiqueta con el entorno activo.

`npm run start:prod` sirve la versión optimizada de producción en http://localhost:4200 y, con
`proxy.conf.json`, reenvía las llamadas a `/api` al backend en `localhost:8080` (simula que ambos están
en el mismo dominio, como en el servidor real).

## Estructura

```
src/app/
├── core/
│   ├── entities/  una interfaz por entidad del sistema: usuario, categoria, producto, pedido,
│   │              ingrediente, movimiento-inventario, alerta (misma forma que las tablas y el API)
│   ├── models/    datos que se envían al API (login, crear cuenta, pedido, producto)
│   ├── services/  AuthService, UsuarioService, CatalogoService, PedidoService, CocinaService
│   ├── interceptors/  agrega el token JWT a cada petición
│   ├── guards/    authGuard (sesión), adminGuard y rolGuard (por rol)
│   └── util/      formato de pesos y horas
├── components/    componentes reutilizables (panel de marca)
├── layout/        estructura con menú lateral (cada rol ve solo sus módulos)
└── pages/         login, recuperar-contrasena, inicio, usuarios, productos (ADMIN),
                   pedidos (MESERO y ADMIN), cocina (COCINA y ADMIN)
```
