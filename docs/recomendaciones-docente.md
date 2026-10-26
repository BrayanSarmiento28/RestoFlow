# Recomendaciones del docente · Sprint 4

**Proyecto Integrador · Universidad de Cundinamarca · 2026-II**
**Autor:** Brayan Estiven Sarmiento Vargas

Antes del Sprint 5 (RestoFlow AI), el docente pidió:

| # | Recomendación | Dónde está |
|---|---------------|------------|
| 1 | Documentar los 12 factores y mostrar dónde se implementan | [`docs/doce-factores.md`](doce-factores.md) |
| 2 | Logs en el backend | [Sección 1](#1-logs-en-el-backend) |
| 3 | Pruebas unitarias: JUnit en el backend y Jest en el frontend | [Sección 3](#3-pruebas-unitarias-con-junit-backend) y [sección 4](#4-pruebas-unitarias-con-jest-frontend) |
| 4 | Implementar una API externa | [Sección 2](#2-api-externa-open-food-facts) |
| 5 | Análisis de calidad con SonarQube (Docker) | [`docs/sonarqube.md`](sonarqube.md) |
| 6 | Principios SOLID en el código | [`docs/principios-solid.md`](principios-solid.md) |

### Comandos para la sustentación

```powershell
# Backend: pruebas JUnit + informe de cobertura (abrir backend\target\site\jacoco\index.html)
cd backend
.\mvnw.cmd verify
# Si no hay Maven Wrapper, la misma tarea con Docker:
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-17 mvn -B verify

# Frontend: pruebas Jest + informe de cobertura (abrir frontend\coverage\jest\index.html)
cd ..\frontend
npm run test:jest:cobertura

# Todo el sistema
cd ..
docker compose up -d --build
docker compose logs -f backend
```

---

## 1. Logs en el backend

### Qué se implementó

| Pieza | Archivo | Qué hace |
|-------|---------|----------|
| Configuración de Logback | `backend/src/main/resources/logback-spring.xml` | Formato de las líneas, consola, JSON (perfil `json`) y archivos con rotación |
| Log de peticiones | `infrastructure/logging/RegistroPeticionesFilter.java` | Una línea por petición HTTP con id, estado, duración y usuario |
| Usuario en cada línea | `infrastructure/security/JwtAuthenticationFilter.java` | Pone el correo del usuario autenticado en el contexto del log (MDC) |
| Auditoría | `application/service/Auditoria.java` | Eventos del negocio: sesiones, cuentas, pedidos, ventas, inventario, alertas y consultas a Open Food Facts |
| Docker | `backend/Dockerfile`, `docker-compose.yml` | Carpeta `/app/logs` en el volumen `backend_logs` |

### Formato de una línea

```
2026-10-20 10:15:02.341  INFO [a1b2c3d4] [mesero@restoflow.com] restoflow.auditoria : evento=PEDIDO_REGISTRADO pedido=15 mesa=4 mesero=3 total=32000
│                        │     │          │                      │                     └ mensaje (clave=valor)
│                        │     │          │                      └ quién escribe la línea (logger)
│                        │     │          └ usuario que hizo la petición
│                        │     └ id de la petición (igual en todas sus líneas y en el encabezado X-Request-Id)
│                        └ nivel: ERROR · WARN · INFO · DEBUG
└ fecha y hora
```

Con `SPRING_PROFILES_ACTIVE=prod,json` la misma información sale en **JSON (formato ECS)**, una línea por evento, para
herramientas de monitoreo.

### Niveles

| Nivel | Cuándo se usa en RestoFlow |
|-------|----------------------------|
| ERROR | Respuesta 5xx; error no controlado (`GlobalExceptionHandler`) |
| WARN | Respuesta 4xx; petición de más de 1 s; inicio de sesión rechazado; alerta creada; faltante en una venta; Open Food Facts sin respuesta |
| INFO | Peticiones correctas; eventos de auditoría; consultas a Open Food Facts con su duración; arranque |
| DEBUG | Detalle para desarrollar (solo en el perfil `dev`) |

### Decisiones (para la sustentación)

- **SLF4J + Logback:** vienen con Spring Boot, así que no hubo que agregar dependencias. SLF4J es una fachada: la capa
  de aplicación no depende de Logback, y eso respeta la arquitectura hexagonal.
- **Consola primero (factor XI):** Docker recoge la consola. Los archivos son un respaldo, y la auditoría se guarda
  90 días.
- **Id por petición:** con muchos meseros trabajando al tiempo, permite filtrar todas las líneas de una sola petición.
- **Filtro antes que Spring Security:** así también se registran los 401 y los 403.
- **Seguridad:**
  - nunca se registran contraseñas, tokens ni códigos;
  - el id de petición que manda el cliente se valida;
  - al texto del usuario se le quitan los saltos de línea.

### Cómo mostrarlo

1. Con `docker compose logs -f backend` abierto, iniciar sesión, registrar un pedido y cobrarlo. Aparecen
   `INICIO_SESION`, `PEDIDO_REGISTRADO`, `VENTA_REGISTRADA`, `MOVIMIENTO_INVENTARIO` y, si un insumo llega al mínimo,
   `ALERTA_CREADA`.
2. `docker compose exec backend tail -n 20 /app/logs/auditoria.log`

---

## 2. API externa: Open Food Facts

### Qué hace y por qué esta API

[Open Food Facts](https://world.openfoodfacts.org) es una base de datos abierta y gratuita de productos de
supermercado, con millones de productos de muchos países, entre ellos Colombia. Se consulta por **código de barras**.

En RestoFlow, el personal de inventario registra un insumo nuevo así:

1. En **Inventario → Nuevo insumo** escribe (o escanea con un lector USB) el código de barras del empaque.
2. Pulsa **Buscar**. RestoFlow consulta Open Food Facts y llena solos el **nombre** (por ejemplo
   "Arroz blanco · Diana") y la **unidad de medida** según el empaque ("1 kg" → kilogramos, "500 ml" → mililitros).
   También muestra la foto, la marca y la cantidad.
3. Revisa los datos, escribe el stock mínimo y guarda.

Por qué esta API:

- Está directamente relacionada con el inventario (RF-05): se registran insumos más rápido y con menos errores de
  digitación.
- Es gratuita y **no pide clave**, así que no hay secretos que proteger.

### Diseño hexagonal

```
Inventario (Angular) ── GET /api/inventario/catalogo-externo/{codigo} ──► CatalogoExternoController
                                                        │
                                        BuscarProductoExternoUseCase (puerto de entrada)
                                                        │
                                        BuscarProductoExternoService  ← valida el código antes de ir a internet
                                                        │
                                        CatalogoExternoPort (puerto de salida)
                                                        │
                                        OpenFoodFactsAdapter          ← infraestructura: HTTP, JSON, caché
                                                        │
                                        world.openfoodfacts.org/api/v2/product/{codigo}
```

- **Dominio** (`ProductoExterno`). Las reglas no dependen de la API:
  - el código debe tener 8, 12, 13 o 14 números;
  - la unidad sugerida se deduce de la cantidad del empaque;
  - el nombre sugerido agrega la marca y tiene máximo 100 caracteres.
- **Seguridad:** solo **INVENTARIO** y **ADMIN** pueden consultarla. El navegador nunca llama a Open Food Facts
  directamente: solo habla con el backend de RestoFlow.

### Respuestas del endpoint

| Caso | Respuesta |
|------|-----------|
| Producto encontrado | **200** con `nombre`, `marca`, `cantidad`, `imagenUrl`, `nombreSugerido`, `unidadSugerida` |
| Código con letras o con un largo que no existe | **400** "El código de barras debe tener 8, 12, 13 o 14 números" (no se llama a internet) |
| Open Food Facts no tiene ese código | **404** "No se encontró ningún producto con el código…" |
| Open Food Facts caído, lento (más de 4 s) o con límite de consultas (429) | **503** "Open Food Facts no está disponible… escriba los datos a mano" |

### Resiliencia

- **Tiempo máximo de espera:** 4 s (`APIS_TIEMPO_ESPERA`).
- **Caché:** 24 h si el producto existe y 1 h si no existe. Guarda máximo 500 códigos en memoria.
- **`User-Agent` propio:** `RestoFlow/0.3.0 (Proyecto Integrador UdeC)`, como pide Open Food Facts.
- **Log:** cada consulta queda con su duración, y los fallos como WARN.
- **Si la API falla, RestoFlow sigue igual:** el usuario puede crear el insumo escribiendo los datos.

### Cómo mostrarlo

1. Con un usuario de inventario o el administrador: **Inventario → Nuevo insumo**, escribir `7702011012340` o el código
   de cualquier producto de la alacena y pulsar **Buscar**.
2. En Swagger: **Catálogo externo → GET /api/inventario/catalogo-externo/{codigo}**, por ejemplo con `4006381333931`.
3. Para mostrar la tolerancia a fallos, desconectar el wifi y buscar: aparece el mensaje del 503 y se puede seguir
   escribiendo a mano.

---

## 3. Pruebas unitarias con JUnit (backend)

**206 pruebas** con JUnit 5, AssertJ y MockMvc, que corren en unos 15 segundos.

- Las **unitarias** no necesitan base de datos ni internet: usan **dobles de prueba** (implementaciones en memoria de
  los puertos). Esa es una ventaja de la arquitectura hexagonal.
- Las de **integración** levantan el API completo con H2 en memoria.

| Capa | Archivo de prueba | Qué comprueba |
|------|-------------------|---------------|
| **Dominio** | `UsuarioTest`, `CodigoRecuperacionTest` | Contraseña segura, cuenta inactiva, código de 6 dígitos que vence y se bloquea tras intentos |
| | `PedidoTest` | Estados de cocina en orden, qué se puede modificar, cancelar o cobrar |
| | `IngredienteTest`, `InventarioDominioTest` | Stock, consumo con faltante, alertas, movimientos, recetas |
| | `CatalogoDominioTest` | Nombres y precios válidos; productos desactivados no se venden |
| | `ProductoExternoTest` | Código de barras válido, unidad sugerida por el empaque (16 casos), nombre sugerido |
| **Aplicación** | `IniciarSesionServiceTest`, `RecuperarContrasenaServiceTest`, `GestionarCuentasServiceTest` | HU-01, HU-02, HU-03 |
| (casos de uso) | `GestionarPedidosServiceTest` | RF-02 y RF-03 |
| | `GestionarCatalogoServiceTest`, `GestionarRecetasServiceTest` | RF-04 y RF-06: categorías repetidas, recetas sin ingredientes duplicados |
| | `GestionarInventarioServiceTest`, `DescontarInventarioServiceTest`, `AlertasServiceTest` | RF-05 a RF-07: entradas, salidas, ajustes, descuento por venta, alertas MEDIA/ALTA que no se repiten y se cierran solas |
| | `ConsultarPanelServiceTest`, `ConsultarHistorialServiceTest` | RF-08 y RF-12: totales, ticket promedio, rangos de fechas |
| | `BuscarProductoExternoServiceTest` | API externa con un doble del catálogo: encontrado, no encontrado, código inválido, servicio caído |
| **Infraestructura** | `OpenFoodFactsAdapterTest` | Servidor HTTP **simulado**: JSON, caché, 404, 500, 429 |
| | `SeguridadAdaptadoresTest` | JWT: token válido, alterado, de otro servidor, vencido; BCrypt |
| | `RegistroPeticionesFilterTest`, `LogsIntegracionTest` | Logs: niveles, id de petición, la contraseña nunca aparece |
| | `RestoFlowPropertiesTest` | Factor III: la configuración inválida impide arrancar |
| **Arquitectura** | `ArquitecturaTest` | Principios SOLID y arquitectura hexagonal: el dominio no depende de frameworks, los controladores usan puertos, los puertos son interfaces pequeñas (ver `docs/principios-solid.md`) |
| | `VentasTest` | Cálculos de ventas compartidos por el panel y el historial |
| | `ActuatorIntegracionTest` | Factor IX: health, liveness y readiness |
| **Integración** | `PedidoFlujoIntegracionTest`, `InventarioFlujoIntegracionTest` | Flujos completos con seguridad y base de datos |
| | `CatalogoExternoIntegracionTest`, `DatosDemoIntegracionTest` | Permisos y errores de la API externa; datos iniciales |

**Cobertura con JaCoCo:**

- `mvn verify` genera `backend/target/site/jacoco/index.html`.
- **Falla si la cobertura de líneas baja del 70 %**. Así se comprueba también en GitHub Actions, que guarda el informe
  como artefacto `cobertura-junit`.

### Ejemplo para explicar

```java
// ProductoExternoTest: una sola prueba revisa 16 empaques distintos (prueba parametrizada)
@ParameterizedTest(name = "\"{0}\" → {1}")
@CsvSource({ "1 kg, KILOGRAMO", "500 g, GRAMO", "1 L, LITRO", "250 ml, MILILITRO", "30 unidades, UNIDAD" /* ... */ })
void unidadSugerida(String cantidad, UnidadMedida esperada) {
    assertThat(producto("Arroz", null, cantidad).unidadSugerida()).isEqualTo(esperada);
}
```

---

## 4. Pruebas unitarias con Jest (frontend)

**80 pruebas** con Jest. Jest prueba **funciones puras y clases sin Angular**: reciben datos y devuelven un resultado.
Para eso, la lógica que estaba dentro de los componentes y servicios se pasó a `src/app/core/util/`, y los componentes
ahora solo la llaman.

| Archivo | Lógica | Pruebas |
|---------|--------|---------|
| `core/util/sesion.ts` | Sesión en el navegador: guardar con o sin "Recordarme", vencimiento, cerrar, rol. Se prueba con almacenes **falsos** en memoria y un reloj fijo | `sesion.test.ts` (9) |
| `core/util/acceso.ts` | Decisiones de los guards (al login, al inicio o pasar) y del interceptor (a qué peticiones se envía el token y cuándo cerrar la sesión) | `acceso.test.ts` (9) |
| `core/services/errores.ts` | Mensaje para el usuario según el error del API (0, 403, validaciones, 404, 503) | `errores.test.ts` (5) |
| `core/util/carrito.ts` | RF-02: agregar, sumar y restar (máximo 99), notas, total, editar un pedido, filtros del menú y de pedidos | `carrito.test.ts` (15) |
| `core/util/inventario-calculos.ts` | RF-05 a RF-07: consumo diario, días que alcanza el stock, estado del insumo, barra, ícono, orden de urgencia | `inventario-calculos.test.ts` (16) |
| `core/util/codigo-barras.ts` | API externa: limpiar el código, largo válido y **dígito de control GS1** (detecta un número mal escrito) | `codigo-barras.test.ts` (7) |
| `core/util/graficas.ts` | RF-08: columnas de "Ventas por hora" y variación frente a ayer | `graficas.test.ts` (6) |
| `core/util/formato.ts` | Pesos colombianos, números y horas de Colombia | `formato.test.ts` (11) |
| `core/entities/pedido.ts` | RF-03: orden de los estados de cocina | `pedido.test.ts` (2) |

**Clases que ahora usan esta lógica** (funcionan igual que antes):

- `AuthService`;
- los guards `authGuard`, `adminGuard` y `rolGuard`;
- `AuthInterceptor`;
- las pantallas `Pedidos`, `Inventario` e `Inicio`.

### Jest y `ng test` juntos

| | Jest | `ng test` (Vitest, de Angular) |
|--|------|--------------------------------|
| Archivos | `*.test.ts` | `*.spec.ts` |
| Para qué | Lógica pura (unitarias) | Componentes de Angular |
| Comando | `npm run test:jest` | `npm test` |

`angular.json` limita `ng test` a los `*.spec.ts`, y `tsconfig.app.json` excluye los `*.test.ts`.

### Configuración

- `frontend/jest.config.js`:
  - entorno `node`;
  - zona horaria `America/Bogota`;
  - **cobertura mínima**: 80 % de líneas y funciones, 60 % de ramas.
- El TypeScript se traduce con `@swc/jest`, porque `ts-jest` aún no soporta oficialmente TypeScript 6. Como swc no
  revisa tipos, se agregó `npm run test:jest:tipos`.
- GitHub Actions ejecuta las pruebas con cobertura antes de compilar. Si una falla, no se despliega. El informe queda
  como artefacto `cobertura-jest`.

### Ejemplo para explicar

```ts
// src/app/core/util/sesion.test.ts · doble de prueba de localStorage y reloj controlado
it('la sesión es vigente hasta que pasa la hora de vencimiento; luego se borra sola', () => {
  sesion.guardar('t', mesero, 60, false);   // Arrange: sesión de 60 segundos
  ahora += 60_000;                          // Act: pasa un minuto
  expect(sesion.vigente()).toBe(true);      // Assert
  ahora += 1;
  expect(sesion.vigente()).toBe(false);
  expect(sesion.token).toBeNull();
});
```
