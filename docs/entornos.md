# Entornos y ruta de ejecución (CI/CD)

RestoFlow maneja tres entornos. El código avanza de uno a otro **solo mediante pull request**, y en cada
paso GitHub Actions comprueba que el backend pase sus pruebas y que el frontend compile.

```
 rama de funcionalidad         develop              preprod                 main
 (backend/… frontend/…)   ──►  DESARROLLO    ──►    PRE-PRODUCCIÓN   ──►    PRODUCCIÓN
                          PR   integración     PR   validación final   PR   sistema real
                                                                           (requiere aprobación)
```

## 1. Qué cambia en cada entorno

| | Desarrollo | Pre-producción | Producción |
|---|---|---|---|
| **Rama de GitHub** | `develop` | `preprod` | `main` |
| **Perfil de Spring Boot** | `dev` (por defecto) | `preprod` | `prod` |
| **Configuración de Angular** | `development` | `preprod` | `production` |
| **Archivo backend** | `application-dev.yml` | `application-preprod.yml` | `application-prod.yml` |
| **Archivo frontend** | `environment.ts` | `environment.preprod.ts` | `environment.prod.ts` |
| **Swagger** | Encendido | Encendido | **Apagado** |
| **Tablas de la BD** | Automáticas (`update`) | Automáticas (`update`) | Solo por script SQL (`none`) |
| **Secretos (JWT, admin)** | Valores de desarrollo | Variables de entorno (con valor de prueba) | **Obligatorios** por variables de entorno |
| **Registros (logs)** | Detallados (`DEBUG`) | Normales (`INFO`) | Mínimos (`WARN`) |
| **Aprobación para desplegar** | No | No | **Sí** (GitHub Environment) |
| **Etiqueta en el menú** | "Entorno: Desarrollo" | "Entorno: Pre-producción" | Sin etiqueta |

## 2. El pipeline (`.github/workflows/ci-cd.yml`)

Se ejecuta en cada **push** y en cada **pull request** hacia `develop`, `preprod` o `main`:

| Trabajo | Qué hace |
|---|---|
| **Backend** | Instala Java 17 y ejecuta `mvn verify`: compila, corre las pruebas automáticas (con H2 en memoria) y genera el `.jar` |
| **Frontend** | Instala Node 24, ejecuta `npm ci` y compila Angular con la configuración del entorno según la rama |
| **Desplegar** | Solo en *push* (cuando el pull request ya se unió). Usa el *GitHub Environment* de la rama; en producción espera la aprobación de un revisor |

Si las pruebas fallan, el pull request queda en rojo y no se debe unir. Así ningún error llega a producción.

> El paso **Desplegar** empaqueta los artefactos del entorno. Cuando el equipo tenga servidores, ahí se agrega
> la copia del `.jar` y de la carpeta `dist` a cada servidor.

## 3. Cómo se ejecuta cada entorno en un computador

**Backend**

| Entorno | VS Code | Maven |
|---|---|---|
| Desarrollo | Ejecutar y depurar → **Backend RestoFlow** | `mvn spring-boot:run` |
| Pre-producción | Ejecutar y depurar → **Backend RestoFlow (pre-producción)** | `mvn spring-boot:run -Dspring-boot.run.profiles=preprod` |
| Producción | — (solo en el servidor) | `java -jar restoflow-backend-0.1.0.jar --spring.profiles.active=prod` con las variables de entorno |

Variables de producción: `JWT_SECRET`, `ADMIN_PASSWORD`, `CORS_ORIGINS`, `DB_HOST`, `DB_NAME`, `DB_USER`,
`DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`.

**Frontend** (dentro de `frontend/`)

| Entorno | Comando |
|---|---|
| Desarrollo | `npm start` |
| Pre-producción | `npm run start:preprod` (o `npm run build:preprod`) |
| Producción | `npm run build:prod` |

**Swagger:** http://localhost:8080/swagger-ui.html (desarrollo y pre-producción).

## 4. Configuración de GitHub (una sola vez)

1. **Crear las ramas** `develop` y `preprod` a partir de `main` y subirlas.
2. **Settings → Environments:** crear `desarrollo`, `pre-produccion` y `produccion`. En `produccion`, activar
   **Required reviewers** y agregar a los integrantes, para que publicar exija aprobación.
3. **Settings → Branches → Add rule** para `main` y `preprod`: **Require a pull request before merging** y
   **Require status checks to pass** (seleccionar los trabajos *Backend* y *Frontend*).
4. **Settings → General → Default branch:** dejar `develop` como rama por defecto para el trabajo diario.

## 5. Flujo de trabajo diario

1. Crear la rama de la funcionalidad desde `develop` (ej. `frontend/pedidos`).
2. Terminar, hacer commit y abrir un **pull request hacia `develop`**. El pipeline corre las pruebas.
3. Al cerrar el sprint, abrir un **pull request `develop` → `preprod`** y validar la versión completa.
4. Si todo está bien, abrir un **pull request `preprod` → `main`**. Un integrante aprueba el despliegue a producción.
