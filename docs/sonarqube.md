# Análisis de calidad con SonarQube

**Proyecto Integrador · Universidad de Cundinamarca · 2026-II**
**Autor:** Brayan Estiven Sarmiento Vargas

## ¿Qué es SonarQube y para qué lo usamos?

SonarQube revisa el código fuente **sin ejecutarlo** (análisis estático) y reporta:

| Qué mide | Ejemplo en RestoFlow |
|----------|----------------------|
| **Bugs** (fiabilidad) | Una variable que puede ser `null` y se usa sin revisar |
| **Vulnerabilidades y puntos críticos de seguridad** | Contraseñas escritas en el código, inyección SQL, CORS abierto |
| **Code smells** (mantenibilidad) | Métodos demasiado largos o complejos, código muerto, nombres confusos |
| **Cobertura** | Qué porcentaje del código ejecutan las pruebas (JUnit con JaCoCo y Jest) |
| **Duplicación** | Bloques de código copiados y pegados |

Con eso calcula una calificación de **A a E** por categoría y aplica una **Quality Gate** ("Sonar way"): un semáforo
que dice si el código cumple el nivel mínimo de calidad.

RestoFlow se analiza como **dos proyectos**:

| Proyecto | Lenguaje | Cobertura que usa |
|----------|----------|-------------------|
| `restoflow-backend` | Java 17 + Spring Boot | JaCoCo (pruebas JUnit) |
| `restoflow-frontend` | TypeScript + Angular (HTML, SCSS) | Jest (`coverage/jest/lcov.info`) |

## Cómo está montado

| Archivo | Qué hace |
|---------|----------|
| `docker-compose.sonar.yml` | Servidor **SonarQube Community Build 26.9** + **PostgreSQL 17** en Docker, separados de la aplicación (proyecto `restoflow-sonar`) |
| `backend/pom.xml` | Plugin `sonar-maven-plugin` 5.8 con versión fija y propiedades del proyecto (clave, servidor, informe de JaCoCo) |
| `frontend/sonar-project.properties` | Qué carpetas analizar, cuáles son pruebas y dónde está la cobertura de Jest |
| `analizar-calidad.ps1` | Ejecuta todo con un comando: pruebas + cobertura + análisis del backend y del frontend |

- El backend se analiza con **Maven** (`mvnw`) en el computador.
- El frontend se analiza con el escáner oficial en **Docker** (`sonarsource/sonar-scanner-cli`), conectado a la red de
  SonarQube.
- El **token** de SonarQube nunca se guarda en un archivo: llega por la variable `SONAR_TOKEN` o se escribe al ejecutar
  el script (factor III de los 12 factores).

## Paso a paso

### 1. Encender SonarQube (una vez por sesión de trabajo)

Con Docker Desktop abierto, en la raíz del proyecto:

```powershell
docker compose -f docker-compose.sonar.yml up -d
docker compose -f docker-compose.sonar.yml ps
```

La primera vez descarga las imágenes (más de 1 GB) y tarda 2 o 3 minutos en arrancar. Está listo cuando esto responde
`status: UP`:

```powershell
Invoke-RestMethod http://localhost:9000/api/system/status
```

### 2. Primer ingreso y token (solo la primera vez)

1. Abrir **http://localhost:9000** e ingresar con usuario `admin` y contraseña `admin`.
2. SonarQube pide cambiar la contraseña: poner una nueva y guardarla.
3. Arriba a la derecha: **A (avatar) → My Account → Security**.
4. En **Generate Tokens**:
   - Name: `restoflow-local`
   - Type: **Global Analysis Token**
   - Expires in: 30 days
5. **Generate** y copiar el token, que empieza por `sqa_`. Solo se muestra una vez.

Para no escribirlo en cada análisis, se puede dejar en la sesión de PowerShell; al cerrar la ventana se borra:

```powershell
$env:SONAR_TOKEN = "sqa_...el token..."
```

### 3. Analizar

En la raíz del proyecto:

```powershell
powershell -ExecutionPolicy Bypass -File .\analizar-calidad.ps1
```

El script:

1. comprueba que SonarQube responda;
2. pide el token, si no está en `SONAR_TOKEN`;
3. en el **backend** ejecuta `mvnw verify sonar`: las 206 pruebas JUnit, la cobertura de JaCoCo y el envío del
   análisis;
4. en el **frontend** ejecuta `npm run test:jest:cobertura`: las pruebas Jest con su cobertura;
5. analiza el **frontend** con el escáner en Docker.

Opciones para analizar solo una parte: `-SoloBackend` y `-SoloFrontend`.

Al terminar muestra los enlaces:

- http://localhost:9000/dashboard?id=restoflow-backend
- http://localhost:9000/dashboard?id=restoflow-frontend

### 4. Apagar

```powershell
docker compose -f docker-compose.sonar.yml down
```

Los análisis quedan guardados en los volúmenes de Docker. Agregar `-v` los borra.

## Qué mostrar en la sustentación

1. **Overview** de cada proyecto: Quality Gate (Passed / Failed), Bugs, Vulnerabilities, Code Smells, Coverage y
   Duplications, con su calificación de A a E.
2. **Issues:** abrir uno, ver la regla que lo explica ("Why is this an issue?") y cómo se corrige.
3. **Security Hotspots:** revisar los puntos marcados (por ejemplo, la configuración de CORS) y explicar por qué son
   seguros o cómo se manejan.
4. **Coverage → Code:** el código en verde es el que ejecutan las pruebas y el rojo el que no.
5. **Relación con SOLID** (`docs/principios-solid.md`): las clases pequeñas, con una sola responsabilidad, tienen menos
   complejidad cognitiva y menos *code smells*.

## Primer análisis (26 de octubre de 2026) y correcciones

Los dos proyectos pasaron la Quality Gate desde el primer análisis. Aun así, se corrigió todo lo de **seguridad** y
**fiabilidad** para llegar a la calificación A.

| Indicador | Backend antes | Frontend antes |
|-----------|---------------|----------------|
| Quality Gate | Passed | Passed |
| Security | D (1) | A (0) |
| Reliability | C (6) | C (7) |
| Maintainability | A (76) | A (32) |
| Coverage | 89,4 % | 39,7 % |
| Duplications | 0,2 % | 0,0 % |

**Backend:**

| Regla | Dónde | Problema | Corrección |
|-------|-------|----------|------------|
| `java:S4502` (seguridad) | `SecurityConfig` | CSRF desactivado | **Revisado, es seguro:** el ataque CSRF usa las cookies que el navegador envía solo. RestoFlow no usa cookies: el token JWT va en el encabezado `Authorization`, que el navegador nunca agrega por su cuenta. La decisión quedó documentada en el código con `@SuppressWarnings("java:S4502")` |
| `java:S5998`, `java:S8786` | `Usuario` | La expresión regular del correo y las de la contraseña podían tardar muchísimo con un texto malicioso (ataque **ReDoS**) | El correo se valida por partes; la contraseña, recorriendo los caracteres. Nueva prueba: un correo de 100.000 caracteres se rechaza en menos de 1 segundo |
| `java:S8786`, `java:S1845` | `ProductoExterno` | Expresión regular con retroceso (*backtracking*), y la constante `CANTIDAD` se confundía con el campo `cantidad` | La cantidad del empaque ("1 kg", "500 g") se lee recorriendo el texto una sola vez, sin expresión regular. Nuevas pruebas: "Peso neto 500 g", "2 x 1 kg", "1 lata"… |
| `java:S8688` | `GlobalExceptionHandler` | `LocalDateTime.now()` sin zona horaria | Usa el `Clock` de la aplicación, con la zona de Colombia |
| `java:S1872` | `ArquitecturaTest` | Comparaba clases por su nombre | Compara por tipo (`isAssignableFrom`) |

**Frontend (accesibilidad):**

| Regla | Dónde | Problema | Corrección |
|-------|-------|----------|------------|
| `Web:InputWithoutLabelCheck` | `pedidos.html`, `productos.html` | Campos sin etiqueta: un lector de pantalla no sabe qué son | Etiquetas `<label for>` ocultas visualmente (`rf-oculto-visual`) |
| `Web:MouseEventWithoutKeyboardEquivalentCheck` | `pedidos.html` | Las filas de pedidos solo se abrían con el mouse | `tabindex="0"`, Enter y Espacio abren la fila, y se ve el foco |
| `typescript:S2933` (mantenibilidad) | 18 archivos | Campos que nunca cambian | Marcados como `readonly` |
| `typescript:S3358` | `inventario-calculos.ts`, `inventario.ts`, `reportes.ts` | Operadores ternarios anidados, difíciles de leer | Una tabla de signos y un `switch`. Además, `reportes.ts` dejó de repetir el código de `inventario-calculos.ts` |
| `typescript:S7776`, `S4043` | `codigo-barras.ts` | Lista en vez de `Set`; `reverse()` dentro de otra expresión | `Set.has()` y `reverse()` en su propia línea |
| `css:S4667` | `app.scss` | Archivo de estilos vacío | Eliminado |

**La segunda pasada:** el segundo análisis marcó la Quality Gate del backend como **Failed**, porque en el código nuevo
debe haber **cero** problemas y la primera corrección de `ProductoExterno` todavía tenía dos. Se corrigió de raíz
(sin expresión regular). Así funciona la Quality Gate: evita que entren problemas nuevos, aunque el resto del código
esté bien.

**Lo que queda, y por qué:**

- **Cobertura del frontend (≈40 %):** Jest prueba la lógica pura (`core/util`, con 98 % de cobertura). Las pantallas
  (componentes de Angular) solo muestran datos y llaman a esa lógica.
- **`java:S6809` (10):** un método `@Transactional` llamado desde la misma clase. En RestoFlow es correcto, porque
  toda la clase ya está dentro de una transacción.
- **`java:S5778` (34):** son de las pruebas (`assertThrows` con varias llamadas dentro de la lambda). Es estilo y no
  afecta el sistema.

## Problemas frecuentes

| Síntoma | Causa y solución |
|---------|------------------|
| `SonarQube no responde` | No está encendido o aún arranca: `docker compose -f docker-compose.sonar.yml up -d` y esperar 2-3 minutos |
| El contenedor `restoflow-sonarqube` se reinicia solo | Poca memoria para Docker: en Docker Desktop → Settings → Resources, dar al menos 4 GB |
| `Not authorized` / `401` en el análisis | Token vencido o mal copiado: generar uno nuevo (paso 2) |
| `network restoflow-sonar_sonar-red not found` | SonarQube no se levantó con `docker-compose.sonar.yml`: repetir el paso 1 |
| La cobertura del frontend sale en 0 % | Debe existir `frontend/coverage/jest/lcov.info`: el script lo genera; con `-SoloFrontend` también |
| `running scripts is disabled on this system` | Ejecutar con `powershell -ExecutionPolicy Bypass -File .\analizar-calidad.ps1` |

## Por qué SonarQube no está en GitHub Actions

El SonarQube de este proyecto es **local** (corre en Docker en el computador del desarrollador), y GitHub Actions no
puede llegar a `localhost`. Para tenerlo en el pipeline habría que:

- publicar SonarQube en un servidor, o usar **SonarQube Cloud**, que es gratuito para repositorios públicos;
- guardar el token como secreto `SONAR_TOKEN` en GitHub;
- agregar un paso `mvn sonar:sonar` al trabajo del backend.

Queda como mejora para cuando el proyecto tenga servidor.
