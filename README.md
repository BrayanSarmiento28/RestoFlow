<p align="center"><img src="docs/identidad/logo-horizontal.png" alt="RestoFlow" width="360"></p>

# RestoFlow

Plataforma web inteligente para la automatización y gestión operativa de restaurantes.

**Proyecto integrador**, Ingeniería de Sistemas y Computación, Universidad de Cundinamarca (UDEC), 2026-II.
Asignaturas: Ingeniería de Software y Sistemas de Información.

| Integrante | Responsabilidad |
|---|---|
| Brayan Estiven Sarmiento Vargas | Backend, frontend, despliegue y documentación |

## En producción · versión 1.1.1

| Qué | Dónde |
|---|---|
| Aplicación | http://18.222.37.70 (AWS EC2, región Ohio) |
| Estado del API | http://18.222.37.70/api/salud |
| Versión publicada | [`v1.1.1`](https://github.com/BrayanSarmiento28/RestoFlow/releases/tag/v1.1.1) |

El servidor corre los mismos contenedores de Docker Compose que el entorno local, con el perfil `prod`. Guía en
[`docs/despliegue-aws.md`](docs/despliegue-aws.md).

**Tecnologías:** Java 17 · Spring Boot 3.5 con arquitectura hexagonal · Angular · SQL Server 2022 · Docker ·
Google Gemini (RestoFlow AI) · Apache JMeter (pruebas de carga) · SonarQube · AWS EC2.

## Estructura del repositorio

```
RestoFlow/
├── backend/    API REST en Spring Boot con arquitectura hexagonal (puertos y adaptadores)
├── frontend/   Aplicación web en Angular que consume el API
├── docker/     Scripts que usa Docker Compose (inicialización de SQL Server)
└── docs/       Documentación técnica: sprints, historias de usuario y modelo de datos
```

El frontend y el backend son **aplicaciones independientes** que se comunican por HTTP (JSON) con autenticación JWT:

```
Angular (Nginx)  ──HTTP/JSON──►  Spring Boot (/api)  ──JPA──►  SQL Server
```

En el servidor solo Nginx está expuesto a internet (puerto 80): reenvía `/api` al backend, y el backend y SQL Server
quedan cerrados dentro de la red de Docker.

## Ejecutar con Docker

Toda la solución (SQL Server, backend y frontend) se levanta con Docker Compose:

```
Copy-Item .env.example .env
docker compose up -d --build
```

Aplicación en http://localhost:4200 y Swagger en http://localhost:8080/swagger-ui.html.
Detalle y buenas prácticas aplicadas en [`docs/docker.md`](docs/docker.md).

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

| Documento | Alcance | Estado |
|---|---|---|
| [`sprint-1.md`](docs/sprint-1.md) | RF-01 Autenticación: iniciar sesión, recuperar contraseña, creación de cuentas por el administrador | Terminado |
| [`sprint-2.md`](docs/sprint-2.md) | Entidades de todo el sistema · RF-02 Pedidos · RF-03 Estados de cocina · RF-04 Comandas · menú de productos · identidad visual | Terminado |
| [`sprint-3.md`](docs/sprint-3.md) | RF-05 Inventario · RF-06 Descuento automático · RF-07 Alertas · RF-08 Panel administrativo · RF-12 Historial | Terminado |
| [`sprint-5.md`](docs/sprint-5.md) | RF-09 Estadísticas · RF-10 Análisis · RF-11 Recomendaciones (RestoFlow AI) | Terminado |
| [`sprint-6.md`](docs/sprint-6.md) | Avisos por rol, Google Gemini, pruebas de carga y estrés, despliegue en AWS y versión 1.1.1 | Terminado |

## Calidad y validación

| Tema | Documento |
|---|---|
| Validación de los 19 requerimientos (12 funcionales y 7 no funcionales) | [`docs/validacion-requisitos.md`](docs/validacion-requisitos.md) |
| Pruebas de carga y estrés con Apache JMeter en AWS | [`docs/pruebas/carga-y-estres.md`](docs/pruebas/carga-y-estres.md) |
| Rendimiento con un usuario | [`docs/pruebas/rendimiento.md`](docs/pruebas/rendimiento.md) |
| Análisis de calidad con SonarQube | [`docs/sonarqube.md`](docs/sonarqube.md) |
| Principios SOLID y los doce factores | [`docs/principios-solid.md`](docs/principios-solid.md) · [`docs/doce-factores.md`](docs/doce-factores.md) |

**Pruebas automáticas:** 316 en el backend (JUnit 5, integración con H2 y MockMvc, y pruebas que vigilan las
reglas de la arquitectura hexagonal) y unas 250 en el frontend (Jest).

El **manual de usuario** se entrega aparte, en PDF.
