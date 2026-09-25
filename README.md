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
y se integra a `main` mediante *pull request*.

| Rama | Uso |
|---|---|
| `main` | Versión estable que se presenta en cada comité |
| `backend/<funcionalidad>` | Trabajo del backend (ej. `backend/autenticacion`) |
| `frontend/<funcionalidad>` | Trabajo del frontend (ej. `frontend/autenticacion`) |

Convención de mensajes de commit: `feat:` nueva funcionalidad · `fix:` corrección · `docs:` documentación ·
`test:` pruebas · `chore:` configuración.

## Avance

| Sprint | Alcance | Estado |
|---|---|---|
| 1 | RF-01 Autenticación: iniciar sesión, recuperar contraseña, creación de cuentas por el administrador | En curso |

Detalle en [`docs/sprint-1.md`](docs/sprint-1.md).
