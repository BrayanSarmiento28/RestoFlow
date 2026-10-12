<p align="center"><img src="docs/identidad/logo-horizontal.png" alt="RestoFlow" width="360"></p>

# RestoFlow

Plataforma web inteligente para la automatización y gestión operativa de restaurantes.

**Proyecto integrador**, Ingeniería de Sistemas y Computación, Universidad de Cundinamarca (UDEC), 2026-II.
Asignaturas: Ingeniería de Software y Sistemas de Información.

| Integrante | Responsabilidad |
|---|---|
| Brayan Estiven Sarmiento Vargas | Backend (Java 17 · Spring Boot 3 · Arquitectura hexagonal · SQL Server) |
| Juan Sebastián Patiño Ocampo | Frontend (Angular · diseño UI/UX en Figma) |

## Estructura del repositorio

```
RestoFlow/
├── backend/    API REST en Spring Boot con arquitectura hexagonal (puertos y adaptadores)
├── frontend/   Aplicación web en Angular que consume el API
└── docs/       Documentación técnica: sprints, historias de usuario y modelo de datos
```

El frontend y el backend son **aplicaciones independientes** que se comunican por HTTP (JSON) con autenticación JWT:

```
Angular (localhost:4200)  ──HTTP/JSON──►  Spring Boot (localhost:8080/api)  ──JPA──►  SQL Server
```

## Metodología

Scrum con sprints de 2 semanas durante 16 semanas. Cada funcionalidad se desarrolla en una rama propia
y avanza por tres entornos mediante *pull request*:

```
backend/… · frontend/…  ──PR──►  develop  ──PR──►  preprod  ──PR──►  main
     (funcionalidad)           desarrollo       pre-producción     producción
```

| Rama | Entorno | Uso |
|---|---|---|
| `main` | Producción | Versión estable que se presenta en cada comité (requiere aprobación) |
| `preprod` | Pre-producción | Validación final antes de publicar |
| `develop` | Desarrollo | Integración del trabajo de backend y frontend |
| `backend/<funcionalidad>` | — | Trabajo del backend (ej. `backend/autenticacion`) |
| `frontend/<funcionalidad>` | — | Trabajo del frontend (ej. `frontend/autenticacion`) |

Cada push o *pull request* ejecuta el pipeline de GitHub Actions (pruebas del backend y compilación del
frontend con la configuración del entorno). Detalle en [`docs/entornos.md`](docs/entornos.md).

Convención de mensajes de commit: `feat:` nueva funcionalidad · `fix:` corrección · `docs:` documentación ·
`test:` pruebas · `chore:` configuración.

## Avance

| Sprint | Alcance | Estado |
|---|---|---|
| 1 | RF-01 Autenticación: iniciar sesión, recuperar contraseña, creación de cuentas por el administrador | Terminado |
| 2 | Entidades de todo el sistema · RF-02 Pedidos · RF-03 Estados de cocina · RF-04 Comandas · menú de productos · identidad visual | Terminado |

Detalle en [`docs/sprint-1.md`](docs/sprint-1.md) y [`docs/sprint-2.md`](docs/sprint-2.md).
