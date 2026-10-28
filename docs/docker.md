# RestoFlow con Docker

RestoFlow se ejecuta completo en contenedores: base de datos, backend y frontend se levantan con un solo comando.

```
                    navegador
                        │  http://localhost:4200
              ┌─────────▼──────────┐
              │ frontend (Nginx)   │  Angular compilado · /api se reenvía al backend
              └─────────┬──────────┘
                        │  red interna "restoflow-red"
              ┌─────────▼──────────┐
              │ backend (Java 17)  │  API REST Spring Boot · Swagger en :8080
              └─────────┬──────────┘
              ┌─────────▼──────────┐      ┌───────────────┐
              │ db (SQL Server)    │◄─────│ db-init       │ crea la base y las tablas
              └─────────┬──────────┘      └───────────────┘ (se ejecuta una vez y termina)
                 volumen sqlserver_datos
```

## Archivos

| Archivo | Qué hace |
|---|---|
| `backend/Dockerfile` | Construye la imagen del backend en dos etapas: Maven compila el `.jar` y la imagen final solo lleva Java (JRE) y el `.jar` |
| `frontend/Dockerfile` | Construye la imagen del frontend en dos etapas: Node compila Angular y la imagen final solo lleva Nginx con los archivos compilados |
| `frontend/nginx.conf` | Sirve Angular, redirige las rutas internas a `index.html` y reenvía `/api` al backend |
| `backend/.dockerignore` · `frontend/.dockerignore` | Excluyen de la imagen compilados, `node_modules`, `.git` y archivos con secretos |
| `docker-compose.yml` | Orquesta los 4 servicios: `db`, `db-init`, `backend` y `frontend` |
| `docker-compose.bd-local.yml` | Variante: backend y frontend en Docker usando el SQL Server del computador |
| `docker/sqlserver/00_inicializar.sql` | Crea la base de datos y el usuario de la aplicación con la contraseña del `.env` |
| `.env.example` | Plantilla de variables para Compose (contraseñas, perfil, puertos) |

## Cómo ejecutarlo

Requisito: **Docker Desktop** instalado y abierto (con al menos 4 GB de memoria; SQL Server necesita 2 GB).

```powershell
cd C:\ruta\a\RestoFlow
Copy-Item .env.example .env        # una sola vez; cambiar las contraseñas
docker compose up -d --build       # construye las imágenes y levanta todo
docker compose ps                  # estado de los servicios (esperar "healthy")
```

La primera vez tarda varios minutos porque descarga las imágenes base y las dependencias.

| Qué | Dirección |
|---|---|
| Aplicación | http://localhost:4200 |
| Swagger (perfil `preprod`) | http://localhost:8080/swagger-ui.html |
| Estado del backend | http://localhost:8080/api/salud · http://localhost:8080/actuator/health |
| SQL Server desde SSMS | servidor `localhost,14330`, usuario `sa` y la contraseña `MSSQL_SA_PASSWORD` del `.env` |

Usuario inicial: `admin@restoflow.com` con la contraseña `ADMIN_PASSWORD` del `.env`.

Otros comandos útiles:

```powershell
docker compose logs -f backend     # ver los registros del backend (ahí sale el código de recuperación)
docker compose down                # detener todo (los datos se conservan en el volumen)
docker compose down -v             # detener y BORRAR la base de datos
docker compose build --no-cache    # reconstruir las imágenes desde cero
```

## Mantener Docker y el modo local iguales

RestoFlow se puede ejecutar de dos formas. Las dos usan **el mismo código**; lo que puede cambiar es la base de datos.

| Forma | Comando | Base de datos |
|---|---|---|
| Local | `cd backend; .\mvnw.cmd spring-boot:run` y `cd frontend; npm start` | SQL Server del computador (SQLEXPRESS) |
| Docker con su propia base | `docker compose up -d --build` | Contenedor `restoflow-db` (datos aparte) |
| **Docker con la base local** | `docker compose -f docker-compose.yml -f docker-compose.bd-local.yml up -d --build` | **SQL Server del computador**: mismas cuentas y datos que el modo local |

Dos reglas para que todo vaya parejo:

1. **Código:** Docker no lee el código cada vez; usa una imagen ya construida. Después de un `git pull` o de cualquier
   cambio, levantar siempre con **`--build`**. Sin `--build` aparece la versión anterior.
2. **Datos:** para ver las mismas cuentas en Docker y en local, usar `docker-compose.bd-local.yml`. Requiere en el
   `.env` la variable `DB_LOCAL_PASSWORD` (la contraseña de `restoflow_app` del SQL Server local).

No se pueden tener las dos formas abiertas al tiempo: las dos usan los puertos 8080 y 4200.

| Síntoma con la base local | Causa y solución |
|---|---|
| El backend no arranca: `connect timed out` a `host.docker.internal:1433` | El firewall de Windows bloquea SQL Server. En PowerShell como administrador: `New-NetFirewallRule -DisplayName "SQL Server (Docker)" -Direction Inbound -Protocol TCP -LocalPort 1433 -Action Allow -Profile Private,Domain` |
| `Login failed for user 'restoflow_app'` | `DB_LOCAL_PASSWORD` no coincide con la contraseña de `application-local.yml` |
| Los cambios no aparecen | Faltó `--build`, o el navegador guardó la versión anterior: Ctrl + F5 |

## Buenas prácticas aplicadas

| Buena práctica | Dónde se aplica |
|---|---|
| Imágenes base oficiales y ligeras | `eclipse-temurin:17-jre-alpine`, `node:24-alpine`, `nginxinc/nginx-unprivileged:1.27-alpine`, `mcr.microsoft.com/mssql/server` |
| Construcción en varias etapas (multi-stage) | Los dos Dockerfile: la imagen final no lleva Maven, Node, `node_modules` ni código fuente |
| Aprovechar la caché de capas | Se copia primero `pom.xml` / `package.json` y se descargan las dependencias antes de copiar el código |
| Archivo `.dockerignore` | Uno por imagen: excluye `target/`, `node_modules/`, `.git`, `.env` y `application-local.yml` |
| Ejecutar como usuario sin privilegios | Backend con el usuario `restoflow` (`USER restoflow`); frontend con Nginx *unprivileged* (puerto 8080) |
| Etiquetas versionadas, nunca `latest` | `restoflow-backend:0.3.0`, `restoflow-frontend:0.3.0`, SQL Server `2022-CU23-ubuntu-22.04` |
| `HEALTHCHECK` | Backend (`/actuator/health/readiness`, Actuator), frontend (Nginx) y SQL Server (`sqlcmd`); Compose espera a que cada servicio esté sano antes de iniciar el siguiente |
| Sin credenciales en las imágenes ni en el código | Todas las contraseñas llegan desde `.env`, que está en `.gitignore`; Compose se niega a arrancar si falta una (`${VAR:?}`) |
| Persistencia de datos | Volumen `sqlserver_datos`: los datos sobreviven al reiniciar o recrear los contenedores |
| Red propia | `restoflow-red`: los servicios se comunican por nombre (`db`, `backend`) |
| Seguridad en Nginx | `server_tokens off` y encabezados `X-Content-Type-Options`, `X-Frame-Options` y `Referrer-Policy` |

## Entornos

El perfil del backend se elige con `SPRING_PROFILES_ACTIVE` en el `.env`:

- `preprod` (por defecto): Swagger encendido y menú de ejemplo, ideal para probar y presentar.
- `prod`: Swagger apagado, sin datos de ejemplo, correo por Gmail; las tablas las crean los scripts SQL.

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `required variable ... is missing a value` | Falta el archivo `.env`: `Copy-Item .env.example .env` |
| `port is already allocated` en 8080 o 4200 | El backend o el `npm start` locales están abiertos: detenerlos o cambiar `BACKEND_PUERTO` / `FRONTEND_PUERTO` en el `.env` |
| `db` no llega a *healthy* | La contraseña `MSSQL_SA_PASSWORD` no cumple la política de SQL Server (8+ caracteres con mayúsculas, minúsculas, números y símbolos) o Docker tiene poca memoria |
| `db-init` termina con error | Ver `docker compose logs db-init`; si se cambió `MSSQL_SA_PASSWORD` después del primer arranque, borrar el volumen con `docker compose down -v` |

## Escalar el backend (varias copias)

Ver el factor VIII en `docs/doce-factores.md`:

```powershell
docker compose -f docker-compose.yml -f docker-compose.escalado.yml up -d --build
1..6 | ForEach-Object { (Invoke-RestMethod http://localhost:4200/api/salud).instancia }
```
