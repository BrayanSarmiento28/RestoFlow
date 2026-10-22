# RestoFlow y los 12 factores

**Proyecto Integrador · Universidad de Cundinamarca · 2026-II**
**Autor:** Brayan Estiven Sarmiento Vargas

## ¿Qué es la metodología de los 12 factores?

*The Twelve-Factor App* ([12factor.net/es](https://12factor.net/es/)) reúne doce buenas prácticas para construir
aplicaciones web que:

- se configuran sin tocar el código;
- se despliegan en cualquier servidor o en la nube;
- se escalan con más copias y se reinician sin perder información;
- funcionan igual en el computador del desarrollador y en producción.

Para cada factor, este documento explica:

1. **Qué dice.**
2. **Dónde está implementado en el código de RestoFlow**, con el archivo exacto.
3. **Cómo comprobarlo** en vivo.
4. **Qué prueba automática lo verifica**, cuando la hay.

### Resumen

| # | Factor | Implementación principal en el código | Prueba automática |
|---|--------|----------------------------------------|-------------------|
| I | Código base | Un repositorio, ramas `develop` / `preprod` / `main` | Pipeline en cada *pull request* |
| II | Dependencias | `pom.xml`, `package.json` + `package-lock.json`, imágenes con versión fija, Maven Wrapper | `npm ci` en CI y Docker |
| III | Configuración | **`RestoFlowProperties.java`**: configuración tipada y **validada al arrancar** | `RestoFlowPropertiesTest` |
| IV | Servicios de respaldo | Puertos y adaptadores: SQL Server, Gmail, **Open Food Facts** | `OpenFoodFactsAdapterTest`, `CatalogoExternoIntegracionTest` |
| V | Construir, publicar, ejecutar | CI/CD, Dockerfiles multi-etapa, versión 0.3.0 + `build-info` | `mvn verify` en CI |
| VI | Procesos sin estado | JWT (`SessionCreationPolicy.STATELESS`) | `SeguridadAdaptadoresTest`, `sesion.test.ts` |
| VII | Asignación de puertos | `server.port: ${PORT:8080}`, Tomcat embebido | — |
| VIII | Concurrencia | **`docker-compose.escalado.yml`**: varias copias del backend | `/api/salud` muestra la `instancia` |
| IX | Desechabilidad | **Actuator** (liveness / readiness), `shutdown: graceful`, healthchecks | `ActuatorIntegracionTest` |
| X | Paridad entre entornos | Perfiles `dev` / `preprod` / `prod`, mismas imágenes, `TZ` | — |
| XI | Logs | **`logback-spring.xml`**, id por petición, auditoría, **JSON (ECS)** | `LogsIntegracionTest`, `RegistroPeticionesFilterTest` |
| XII | Procesos de administración | Contenedor `db-init`, scripts SQL, datos iniciales | `DatosDemoIntegracionTest` |

---

## I. Código base · *Una base de código, muchos despliegues*

**Qué dice:** la aplicación vive en **un solo repositorio**, y de ese mismo código salen todos los despliegues.

**En RestoFlow:**

- Un único repositorio (`github.com/BrayanSarmiento28/RestoFlow`) con:
  - `backend/`;
  - `frontend/`;
  - la base de datos (`backend/database/`);
  - Docker (`docker-compose*.yml`, `docker/`);
  - la documentación (`docs/`).
- Cada entorno es una rama del **mismo** código (ver `docs/entornos.md`):

  | Rama | Entorno | Perfil de Spring Boot | Configuración de Angular |
  |------|---------|-----------------------|--------------------------|
  | `develop` | desarrollo | `dev` | `development` |
  | `preprod` | pre-producción | `preprod` | `preprod` |
  | `main` | producción | `prod` | `production` |

- Las tareas se hacen en ramas propias (`sprint-4/...`) y entran por *pull request*. GitHub Actions las prueba
  antes de unirlas.

**Cómo mostrarlo:** ejecutar `git branch -a` y abrir la pestaña *Pull requests* de GitHub.

---

## II. Dependencias · *Declarar y aislar explícitamente las dependencias*

**Qué dice:** todas las librerías se declaran en un archivo; nada depende de algo "que ya estaba instalado".

**En RestoFlow:**

- **Backend:** `backend/pom.xml` declara:
  - Spring Boot 3.5 (web, seguridad, JPA, validación, correo, **Actuator**);
  - el driver de SQL Server;
  - springdoc (Swagger);
  - H2 y JaCoCo, para las pruebas.
- **Frontend:**
  - `frontend/package.json` declara Angular 22, RxJS, Vitest, **Jest** y **@swc/jest**.
  - `package-lock.json` fija la versión exacta de cada paquete.
  - Docker y GitHub Actions usan `npm ci`, que instala **exactamente** lo que dice el lock.
- **Aislamiento:** los Dockerfiles parten de imágenes con versión fija:
  - `maven:3.9-eclipse-temurin-17`;
  - `eclipse-temurin:17-jre-alpine`;
  - `node:24-alpine`;
  - `nginx-unprivileged:1.27-alpine`.
- **Maven Wrapper** (`mvnw` / `mvnw.cmd`): compila el backend sin instalar Maven. Descarga la versión de Maven
  indicada en `.mvn/wrapper/maven-wrapper.properties`. Se genera una sola vez con:

  ```powershell
  docker run --rm -v "${PWD}/backend:/app" -w /app maven:3.9-eclipse-temurin-17 mvn -N wrapper:wrapper -Dtype=only-script
  ```

  Después, en Windows basta con `cd backend; .\mvnw.cmd test`.

**Cómo mostrarlo:** abrir `pom.xml`, `package.json` y la línea `RUN npm ci` de `frontend/Dockerfile`.

---

## III. Configuración · *Guardar la configuración en el entorno*

**Qué dice:** todo lo que cambia entre entornos (contraseñas, direcciones, claves) va en **variables de entorno**,
nunca escrito en el código.

**Implementación en el código:** `backend/src/main/java/com/udec/restoflow/infrastructure/config/RestoFlowProperties.java`

```java
@Validated
@ConfigurationProperties(prefix = "restoflow")
public record RestoFlowProperties(
        @DefaultValue("desarrollo") @NotBlank String entorno,
        @Valid @NotNull Jwt jwt,                 // JWT_SECRET: mínimo 32 caracteres
        @Valid @NotNull Cors cors,               // CORS_ORIGINS
        @Valid @NotNull @DefaultValue Correo correo,           // CORREO_MODO: consola | gmail
        @Valid @NotNull AdminInicial adminInicial,             // ADMIN_EMAIL (correo válido), ADMIN_PASSWORD
        @Valid @NotNull @DefaultValue Logs logs,               // LOG_DIR
        @Valid @NotNull @DefaultValue ApisExternas apisExternas) { ... }   // OPENFOODFACTS_URL
```

- **Un solo lugar:** toda la configuración propia de RestoFlow se lee en esta clase, con nombres y tipos claros.
  Antes estaba repartida en varios `@Value("${...}")` por el código. Ahora la usan `JwtTokenAdapter`,
  `SecurityConfig` (CORS), `GmailCorreoAdapter`, `AdministradorInicialConfig`, `SaludController`, `OpenApiConfig` y
  `ApisExternasConfig`.
- **Validada al arrancar** (*fail fast*): si una variable falta o tiene un valor imposible, la aplicación **no arranca**
  y el log dice exactamente qué está mal. Por ejemplo:
  - una clave JWT corta;
  - un correo mal escrito;
  - `CORREO_MODO=whatsapp`;
  - un tiempo de espera de 0.

  Es mejor fallar al desplegar que descubrir el error con el restaurante abierto.
- **Los secretos no se imprimen:** `toString()` muestra `****` en lugar de la clave JWT y la contraseña del
  administrador.
- **Los valores vienen del entorno:** `application.yml` lee las variables con `${VARIABLE:valor-por-defecto}`:

  | Variable | Para qué |
  |----------|----------|
  | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Conexión a SQL Server |
  | `JWT_SECRET` | Clave para firmar los tokens de sesión |
  | `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Administrador inicial |
  | `CORREO_MODO`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Correo de recuperación |
  | `CORS_ORIGINS` | Direcciones del frontend permitidas |
  | `SPRING_PROFILES_ACTIVE` | Entorno (`dev`, `preprod`, `prod`); agregar `json` para logs en JSON |
  | `LOG_DIR` | Carpeta de los archivos de log |
  | `OPENFOODFACTS_URL`, `APIS_TIEMPO_ESPERA` | API externa |
  | `PORT` | Puerto HTTP del backend |
  | `TZ` | Zona horaria (Docker) |

- **Producción exige sus secretos:** en `application-prod.yml`, `${JWT_SECRET}` y `${ADMIN_PASSWORD}` no tienen valor
  por defecto.
- **Nada secreto en GitHub:** `.gitignore` excluye `.env` y `application-local.yml`. Solo se suben las plantillas
  (`.env.example`, `application-local.example.yml`).

**Prueba automática:** `RestoFlowPropertiesTest` (6 pruebas). Levanta solo esta configuración y comprueba que:

- con lo mínimo arranca y completa los valores por defecto;
- con una clave JWT corta, sin `JWT_SECRET`, con un modo de correo desconocido o con un correo inválido **no arranca**;
- los secretos no aparecen en el texto.

**Límite conocido:** en Angular, la dirección del API (`src/environments/*.ts`) queda fija **al compilar**. Por eso se
compila una vez por entorno. En Docker no afecta: Nginx sirve el frontend y reenvía `/api` al backend.

**Cómo mostrarlo:**

1. En `.env`, poner `JWT_SECRET=corta`.
2. Ejecutar `docker compose up -d backend` y luego `docker compose logs backend`.
3. Aparece *"Property: restoflow.jwt.secret … Reason: debe tener al menos 32 caracteres (variable JWT_SECRET)"* y el
   backend no arranca.

---

## IV. Servicios de respaldo · *Tratarlos como recursos conectables*

**Qué dice:** la base de datos, el correo o una API de terceros son recursos a los que se llega por una dirección
configurable. Cambiar uno por otro no exige tocar el código.

**En RestoFlow** (la arquitectura hexagonal lo facilita: cada servicio está detrás de un **puerto**):

| Servicio | Puerto (interfaz) | Adaptador | Configuración |
|----------|-------------------|-----------|---------------|
| SQL Server | `*RepositoryPort` | `adapter/out/persistence/*PersistenceAdapter` | `DB_HOST`, `DB_PORT`... |
| Correo Gmail | `EnviarCorreoPort` | `GmailCorreoAdapter` / `CorreoConsolaAdapter` | `CORREO_MODO`, `MAIL_*` |
| **Open Food Facts** (API externa) | `CatalogoExternoPort` | `adapter/out/externo/OpenFoodFactsAdapter` | `OPENFOODFACTS_URL`, `APIS_TIEMPO_ESPERA` |

- El correo cambia de "consola" a "Gmail" con **una variable**, sin compilar de nuevo.
- La base de datos de Docker y una instalada en el computador se usan igual: solo cambia `DB_HOST`.
- **Open Food Facts** se conecta así:
  - URL configurable;
  - tiempo máximo de espera (4 s);
  - `User-Agent` propio;
  - caché;
  - si falla, responde 503 con un mensaje claro, y el usuario puede escribir los datos a mano.

**Pruebas automáticas:**

- `OpenFoodFactsAdapterTest` (7), con un servidor HTTP simulado: respuesta correcta, caché, `status 0`, 404, 500 y 429.
- `BuscarProductoExternoServiceTest` (4).
- `CatalogoExternoIntegracionTest`: permisos, 400 y 503 con la API caída.

---

## V. Construir, publicar, ejecutar · *Separar estrictamente cada etapa*

**Qué dice:** **construir** (compilar y probar), **publicar** (versión + configuración) y **ejecutar** son pasos
separados. En producción nunca se compila.

**En RestoFlow:**

1. **Construir**
   - `.github/workflows/ci-cd.yml` corre en cada *push* o *pull request*:
     - Backend: `mvn -B verify` ejecuta las **pruebas JUnit** y la cobertura **JaCoCo** (mínimo 70 %) y genera el `.jar`.
     - Frontend: **pruebas Jest** con cobertura (mínimo 80 %), revisión de tipos y `ng build` por entorno.
   - La primera etapa (`AS build`) de cada Dockerfile compila. La imagen final solo lleva el `.jar` o los archivos de
     `dist`: no lleva Maven, Node ni el código fuente.
2. **Publicar**
   - Versión **0.3.0** en `pom.xml`, en las imágenes (`restoflow-backend:${RESTOFLOW_VERSION}`) y en `.env.example`.
   - El objetivo `build-info` guarda en el `.jar` la versión y la fecha de compilación. Se ven en `/actuator/info`.
   - GitHub guarda los artefactos de cada ejecución: el `.jar`, el `dist` de Angular y los **informes de cobertura**.
3. **Ejecutar:** `docker compose up -d` arranca las imágenes con la configuración de `.env`.

**Cómo mostrarlo:**

- la pestaña *Actions* de GitHub (con los artefactos `cobertura-junit` y `cobertura-jest`);
- `Invoke-RestMethod http://localhost:8080/actuator/info`.

---

## VI. Procesos · *Ejecutar la aplicación como procesos sin estado*

**Qué dice:** el proceso no guarda datos del usuario en memoria entre peticiones; lo que hay que conservar va a la base
de datos.

**En RestoFlow:**

- **No hay sesión en el servidor.** `SecurityConfig.java` usa `SessionCreationPolicy.STATELESS`: cada petición trae su
  token JWT y cualquier copia del backend la valida sola.
- Pedidos, inventario, alertas y códigos de recuperación viven en SQL Server.
- La única memoria del proceso es la **caché** de Open Food Facts, y es descartable: si el proceso se reinicia, se
  vuelve a consultar.

**Pruebas:**

- `SeguridadAdaptadoresTest`: el token se valida solo con la clave, y un token alterado o vencido no sirve.
- `sesion.test.ts` (Jest): la sesión vive en el navegador y vence sola.

**Cómo mostrarlo:** con `docker compose restart backend` el usuario sigue con su sesión, porque su token sigue siendo
válido.

---

## VII. Asignación de puertos · *Publicar servicios mediante la asignación de puertos*

**Qué dice:** la aplicación es autónoma y se publica escuchando en un puerto.

**En RestoFlow:**

- El backend es un `.jar` con Tomcat embebido. Escucha en `server.port: ${PORT:8080}`, así que el puerto se cambia con
  una variable.
- El frontend lo sirve Nginx en el puerto 8080 del contenedor.
- `docker-compose.yml` asigna los puertos del computador con variables:
  - `FRONTEND_PUERTO` (4200);
  - `BACKEND_PUERTO` (8080);
  - `DB_PUERTO` (14330).

**Cómo mostrarlo:** `docker compose ps`.

---

## VIII. Concurrencia · *Escalar mediante el modelo de procesos*

**Qué dice:** para atender más carga se arrancan **más procesos iguales**, en vez de un proceso cada vez más grande.

**Implementación:** `docker-compose.escalado.yml`

```yaml
services:
  backend:
    container_name: !reset null   # varias copias no pueden tener un nombre fijo
    ports: !reset []              # ni el mismo puerto del computador
    deploy:
      replicas: ${BACKEND_REPLICAS:-2}
```

- Como el backend no tiene estado (factor VI), Nginx reparte las peticiones entre las copias, y cualquiera atiende a
  cualquier usuario.
- `GET /api/salud` y `/actuator/info` responden también **`instancia`**: el contenedor que contestó.
- Los datos iniciales se crean **solo si la tabla está vacía**, así que no se duplican aunque haya dos copias.

**Cómo mostrarlo:**

1. Levantar con las dos configuraciones y revisar las copias:

   ```powershell
   docker compose -f docker-compose.yml -f docker-compose.escalado.yml up -d --build
   docker compose ps
   ```

   Aparecen `restoflow-backend-1` y `restoflow-backend-2`.

2. Consultar la salud varias veces:

   ```powershell
   1..6 | ForEach-Object { (Invoke-RestMethod http://localhost:4200/api/salud).instancia }
   ```

   Responden las dos copias.

Se levanta así después de haber iniciado RestoFlow normal una vez, para que la base de datos ya exista.

---

## IX. Desechabilidad · *Arranque rápido y apagado ordenado*

**Qué dice:** los procesos se pueden detener o reiniciar en cualquier momento. Arrancan rápido y, al apagarse,
terminan lo que estaban haciendo.

**En RestoFlow:**

- **Spring Boot Actuator** (`pom.xml` y `application.yml`):

  | Endpoint | Para qué |
  |----------|----------|
  | `/actuator/health` | Estado general (`UP` / `DOWN`), sin detalles internos |
  | `/actuator/health/liveness` | ¿El proceso está vivo? Si no, hay que reiniciarlo |
  | `/actuator/health/readiness` | ¿Puede recibir peticiones? (por ejemplo, ya conectó a la base de datos) |
  | `/actuator/info` | Nombre, versión, fecha de compilación, entorno e instancia |

  Solo se publican esos endpoints. `env`, `beans` y `configprops` **no** existen hacia afuera.
- **Healthcheck de Docker** (`backend/Dockerfile`): usa `/actuator/health/readiness`. Docker solo da el backend por
  listo, y solo entonces arranca el frontend, cuando de verdad puede atender.
- **Apagado ordenado:** con `server.shutdown: graceful` y `spring.lifecycle.timeout-per-shutdown-phase: 20s`, al
  detener el contenedor se terminan las peticiones en curso antes de apagar.
- **La base de datos queda consistente:** cada venta y su descuento de inventario van en una sola transacción. Si el
  proceso muere a la mitad, se deshace todo.
- **Arranque rápido:** la imagen solo lleva el JRE y el `.jar`. `ENTRYPOINT` usa `exec`, para que Java reciba la señal
  de apagado.

**Prueba automática:** `ActuatorIntegracionTest` (4 pruebas):

- health, liveness y readiness responden `UP`;
- no se muestran detalles internos;
- `info` responde;
- los demás endpoints están cerrados.

**Cómo mostrarlo:**

- `Invoke-RestMethod http://localhost:8080/actuator/health/readiness`;
- `docker compose stop backend` y luego, en los logs, *"Commencing graceful shutdown"*.

---

## X. Paridad entre desarrollo y producción · *Mantener los entornos lo más parecidos posible*

**Qué dice:** desarrollo, pruebas y producción deben parecerse lo más posible.

**En RestoFlow:**

- **Mismos servicios:** SQL Server en todos los entornos. Solo las pruebas automáticas usan H2 (`MODE=MSSQLServer`).
- **Mismo código, distinta configuración:** `application-dev.yml`, `application-preprod.yml` y `application-prod.yml`
  solo cambian valores.
- **Mismas imágenes:** `docker compose up` levanta en cualquier computador lo mismo que en un servidor.
- **Misma hora:** `TZ=America/Bogota` en el backend de Docker. Sin esto el contenedor usa UTC, y después de las 7 p. m.
  el "hoy" del Dashboard sería mañana.
- **Poca distancia:** cada *pull request* se prueba enseguida en GitHub Actions.

---

## XI. Logs · *Tratar los logs como un flujo de eventos*

**Qué dice:** la aplicación escribe sus eventos, uno por línea, en la consola. Es el entorno quien los recoge, guarda y
analiza.

**En RestoFlow:**

1. **Consola**, que es la salida principal: `docker compose logs -f backend`.
2. **JSON para máquinas:** con el perfil `json` (`SPRING_PROFILES_ACTIVE=prod,json`) cada línea es un evento en formato
   **ECS** (Elastic Common Schema), listo para Elastic, Grafana Loki o Azure Monitor:

   ```json
   {"@timestamp":"2026-10-22T00:41:46.818Z","log":{"level":"WARN","logger":"restoflow.peticiones"},
    "message":"GET /api/pedidos → 401 en 40 ms · usuario=anónimo","requestId":"a1b2c3d4",
    "service":{"name":"restoflow-backend","version":"0.3.0"}}
   ```

3. **Archivos de respaldo** con rotación, en `LOG_DIR` (en Docker, el volumen `backend_logs`):
   - `restoflow.log`: 30 días;
   - `auditoria.log`: 90 días.
4. **Una línea por petición**, con id, usuario, estado y milisegundos (`RegistroPeticionesFilter`). El id se devuelve
   en el encabezado `X-Request-Id`.
5. **Auditoría del negocio** (`Auditoria.java`). Registra:
   - inicios de sesión, también los rechazados;
   - cuentas creadas y recuperación de contraseña;
   - pedidos, cancelaciones y ventas;
   - movimientos y faltantes de inventario;
   - alertas;
   - consultas a Open Food Facts.
6. **Nunca** se registran contraseñas ni tokens, y el texto del usuario se limpia (contra la inyección de logs).

**Pruebas automáticas:**

- `LogsIntegracionTest` (4): auditoría, línea de petición, `X-Request-Id`, y que la contraseña no aparece.
- `RegistroPeticionesFilterTest` (4): niveles ERROR y WARN, petición lenta, limpieza del contexto, rutas excluidas.

Detalle completo en `docs/recomendaciones-docente.md`.

---

## XII. Procesos de administración · *Tareas de gestión que se ejecutan una vez*

**Qué dice:** las tareas de mantenimiento (crear la base de datos, cargar datos iniciales) son procesos que se ejecutan
**una sola vez**, con el mismo código y la misma configuración.

**En RestoFlow:**

- **Contenedor `db-init`:**
  - ejecuta una vez los scripts SQL versionados (`docker/sqlserver/00_inicializar.sql`,
    `backend/database/01_crear_base_de_datos.sql`, `02_menu_pedidos_inventario.sql`) y termina;
  - el backend espera a que termine bien (`service_completed_successfully`).
- **Datos iniciales con el mismo código de la aplicación:**
  - `AdministradorInicialConfig` crea el primer administrador si no hay usuarios.
  - `MenuInicialConfig` y `InventarioInicialConfig` cargan el menú y el inventario de ejemplo si
    `restoflow.datos-demo=true` y las tablas están vacías.
- **Medición puntual:** `docs/pruebas/medir-rendimiento.ps1`.

**Prueba automática:** `DatosDemoIntegracionTest` comprueba que, con `datos-demo=true`, se cargan productos, insumos y
recetas.

**Cómo mostrarlo:** con `docker compose ps -a`, `db-init` aparece como *Exited (0)*; `docker compose logs db-init`
muestra "Base de datos lista".
