# Sprint 6 · Recomendaciones del comité

**Proyecto Integrador · Universidad de Cundinamarca · 2026-II**
**Autor:** Brayan Estiven Sarmiento Vargas

En el comité del 30 de octubre el docente pidió:

| # | Recomendación | Cómo se resuelve | Estado |
|---|---------------|------------------|--------|
| 1 | Implementar logs que se guarden en la base de datos | Registro de actividad: cada petición queda en la tabla `registro_peticiones` de SQL Server | **Hecho** |
| 2 | Validar API externa | RestoFlow AI en toda la plataforma: avisos por rol + **IA externa Google Gemini** (resumen por rol, preguntas del admin y recomendaciones) | **Hecho** |
| 3 | Validar lógica de la aplicación | Regla nueva de **mesas ocupadas** y pruebas de punta a punta de las reglas del negocio | **Hecho** |
| 4 | Debe tener cobertura externa | Cobertura del frontend completo mayor a 80 % en SonarQube | Parte 3 |

---

## 1. Registro de actividad (logs en la base de datos)

### Qué se guarda

**Todas las peticiones al API** (`/api/...`), también las rechazadas y las anónimas, como un login fallido:

| Columna | Ejemplo | Para qué |
|---------|---------|----------|
| `fecha` | 2026-11-04 10:35:12 | Cuándo |
| `usuario`, `rol` | laura@restoflow.com, MESERO | Quién (vacío si no había sesión) |
| `metodo`, `ruta` | POST `/api/pedidos?mesa=4` | Qué pidió |
| `estado` | 201, 401, 409, 500... | Cómo terminó |
| `duracion_ms` | 85 | Cuánto tardó |
| `request_id` | a1b2c3d4 | El mismo id del log de texto, para cruzar las dos fuentes |

**Nunca** se guarda el cuerpo de la petición (contraseñas, códigos de recuperación) ni el token.
No se registran `/api/salud`, Actuator ni Swagger, porque se consultan cada pocos segundos y solo llenarían la tabla.

### Por qué así (decisiones para la sustentación)

- **En segundo plano (asíncrono).** El usuario recibe su respuesta sin esperar a que se escriba el registro.
  Un solo hilo con una cola de 5.000 registros (`EscritorRegistroAsincrono`). Si la base de datos va lenta y la cola
  se llena, se descarta el registro y queda un aviso en el log de texto: **el restaurante nunca se detiene por el log**.
- **El log de texto sigue igual.** El factor XI de los 12 factores pide que los logs salgan como un flujo de eventos
  (consola y archivo); eso no cambia. La tabla es un **complemento** para consultar y filtrar desde la aplicación.
  Si la base de datos se cae, el log de texto sigue funcionando.
- **Se borra solo.** Cada madrugada (3:30 a. m.) se borra lo que tenga más de 30 días (`REGISTRO_BD_DIAS`), para
  que la tabla no crezca sin límite. La tabla tiene índices por fecha y por usuario.
- **Arquitectura hexagonal.** El filtro web (adaptador de entrada) no conoce JPA: llama al caso de uso
  `RegistrarPeticionUseCase`, y el servicio guarda por el puerto `RegistroPeticionRepositoryPort`, que implementa
  el adaptador JPA. Las reglas (qué es un error, un rechazo o una petición lenta) están en el dominio, en Java puro.
- **Solo el ADMIN** puede ver el registro (`/api/registro/**` en `SecurityConfig`).

### Dónde está

| Capa | Archivo | Qué hace |
|------|---------|----------|
| Dominio | `domain/model/RegistroPeticion.java` | Una petición: valida, recorta la ruta, `esError()`, `esRechazo()`, `esLenta()` |
| Dominio | `domain/model/FiltroRegistro.java` | Filtros: fechas, usuario, método, ruta y tipo (TODAS, EXITOSAS, RECHAZADAS, ERRORES, LENTAS) |
| Dominio | `domain/model/ResumenRegistro.java` | Totales del periodo y una página de resultados |
| Aplicación | `application/port/in/RegistrarPeticionUseCase`, `ConsultarRegistroUseCase`, `LimpiarRegistroUseCase` | Casos de uso (puertos de entrada) |
| Aplicación | `application/port/out/RegistroPeticionRepositoryPort` | Puerto de salida |
| Aplicación | `application/service/RegistroActividadService` | Guarda, consulta (máximo 200 por página) y limpia |
| Infraestructura | `infrastructure/logging/RegistroPeticionesFilter` | Mide cada petición, escribe el log de texto y la envía a la tabla |
| Infraestructura | `infrastructure/logging/EscritorRegistroAsincrono` | Guarda en segundo plano |
| Infraestructura | `infrastructure/adapter/out/persistence/RegistroPeticionPersistenceAdapter` (+ entidad y repositorio JPA) | Tabla `registro_peticiones` |
| Infraestructura | `infrastructure/adapter/in/web/RegistroController` | `GET /api/registro` |
| Infraestructura | `infrastructure/adapter/in/programada/LimpiezaRegistroProgramada` | Limpieza de cada madrugada |
| Base de datos | `backend/database/03_registro_peticiones.sql` | La tabla para producción (en desarrollo la crea Spring Boot) |

### El API

```
GET /api/registro?desde=2026-11-01&hasta=2026-11-04&usuario=laura&metodo=POST&ruta=pedidos&tipo=RECHAZADAS&pagina=0&tamano=50
```

Todo es opcional. Responde un resumen (total, rechazadas, errores, lentas y duración promedio) y una página de
registros, del más reciente al más antiguo. Se puede probar en Swagger (`http://localhost:8080/swagger-ui.html`).

### Configuración (variables de entorno)

| Variable | Por defecto | Para qué |
|----------|-------------|----------|
| `REGISTRO_BD_HABILITADO` | `true` | `false` apaga el registro en la base de datos (el de texto sigue) |
| `REGISTRO_BD_DIAS` | `30` | Días que se conservan |
| `REGISTRO_BD_LIMPIEZA` | `true` | Limpieza automática de cada madrugada |
| `REGISTRO_BD_CRON_LIMPIEZA` | `0 30 3 * * *` | A qué hora se limpia |

### Dónde se consulta: en SQL Server

Los registros **no se muestran en la aplicación**: se revisan directamente en la base de datos (decisión del
proyecto: el registro es para auditoría técnica, no para el personal del restaurante). Consultas útiles en
SQL Server Management Studio:

```sql
-- Lo último que pasó
SELECT TOP 50 fecha, usuario, rol, metodo, ruta, estado, duracion_ms, request_id
FROM registro_peticiones ORDER BY fecha DESC;

-- Rechazos (4xx) y errores del servidor (5xx)
SELECT * FROM registro_peticiones WHERE estado >= 400 ORDER BY fecha DESC;

-- Peticiones lentas (más de 1 segundo)
SELECT * FROM registro_peticiones WHERE duracion_ms > 1000 ORDER BY duracion_ms DESC;

-- Qué hizo un usuario hoy
SELECT * FROM registro_peticiones
WHERE usuario = 'laura@restoflow.com' AND fecha >= CAST(GETDATE() AS DATE) ORDER BY fecha;

-- Resumen por día
SELECT CAST(fecha AS DATE) AS dia, COUNT(*) AS peticiones,
       SUM(CASE WHEN estado BETWEEN 400 AND 499 THEN 1 ELSE 0 END) AS rechazadas,
       SUM(CASE WHEN estado >= 500 THEN 1 ELSE 0 END) AS errores,
       AVG(duracion_ms) AS promedio_ms
FROM registro_peticiones GROUP BY CAST(fecha AS DATE) ORDER BY dia DESC;
```

El backend también deja la consulta disponible en el API (`GET /api/registro`, solo ADMIN, visible en Swagger)
por si más adelante se quiere una herramienta externa.

### Pruebas

| Prueba | Qué comprueba |
|--------|---------------|
| `RegistroActividadDominioTest` (7) | Clasificación 4xx / 5xx / lenta, recorte de la ruta, datos inválidos, filtros |
| `RegistroActividadServiceTest` (5) | Resumen, filtros, paginación (50 por defecto, máximo 200) y limpieza de más de 30 días |
| `RegistroPeticionesFilterTest` (+2) | El filtro envía el registro con usuario, rol, ruta y estado; si el registro falla, la respuesta no se rompe |
| `RegistroActividadIntegracionTest` (3) | De punta a punta con H2: la petición llega a la tabla, el login fallido queda como anónimo con 401, MESERO recibe 403 |

### Cómo probarlo

```powershell
# 1. Backend (crea la tabla solo) y frontend
cd backend
.\mvnw.cmd spring-boot:run
# en otra terminal
cd frontend
npm start
# 2. Usar la aplicación (pedidos, inventario, un login con clave equivocada...)
# 3. En SQL Server Management Studio (actualizar la carpeta Tablas con F5):
#    SELECT TOP 20 * FROM registro_peticiones ORDER BY fecha DESC;
```

---

## 2. RestoFlow AI en toda la plataforma: avisos por rol

El docente recomendó llevar RestoFlow AI a toda la aplicación, no solo a una pantalla. Ahora **cada usuario tiene
una campana** (arriba del menú lateral) con los avisos de su trabajo, calculados con lo que está pasando en el
restaurante en ese momento.

### Qué avisa a cada rol

| Rol | Aviso | Atención (amarillo) | Urgente (rojo) |
|-----|-------|---------------------|----------------|
| Cocina | Pedido demorado frente al **tiempo normal de ese plato** | pasó su tiempo normal | lleva 1,5 veces su tiempo normal (también le llega al mesero de la mesa) |
| Mesero | Pedido listo y sin llevar a la mesa (solo sus mesas) | 3 min | 8 min |
| Mesero | Pedido entregado y sin cobrar (solo sus mesas) | 30 min | 60 min |
| Inventario | Insumo que alcanza para 3 días o menos / insumo en su mínimo, con cuánto comprar | se acaba | en el mínimo |
| Inventario y cocina | Mañana será un día fuerte (20 % o más sobre un día normal) y qué platos se pedirán más | sí | — |
| Admin | Todo lo anterior y la demanda esperada de mañana | | |

### Lo que lo hace "inteligente"

- **Aprende cuánto tarda cada plato** con los pedidos reales de las últimas 2 semanas (desde que se registra hasta
  que cocina lo marca Listo). Una bandeja paisa no se compara con un jugo: cada plato tiene su propio tiempo normal.
- Usa la **mediana**, no el promedio: si un día un pedido se quedó olvidado 2 horas, el promedio se dispara pero la
  mediana casi no cambia. Con menos de 3 pedidos de un plato usa 15 minutos de referencia.
- En un pedido con varios platos manda **el más lento**.
- Reutiliza el análisis de RestoFlow AI del Sprint 5 (consumo de insumos y predicción de demanda).
- **No se guardan**: se calculan al consultar. Cuando la situación se resuelve (el pedido se entrega, se compra el
  insumo) el aviso desaparece solo. Así no hay avisos viejos y cualquier copia del backend responde lo mismo
  (factor VI: procesos sin estado).

### La campana

- Pide los avisos **cada minuto**.
- El número rojo cuenta los avisos importantes que aún no se han visto; si hay algo urgente, la campana late.
- Al abrirla todo queda visto. Si después aparece algo nuevo, vuelve a contar.
- "Ir a atenderlo" lleva a la pantalla correcta, solo si ese rol puede abrirla.

### Dónde está

| Capa | Archivo | Qué hace |
|------|---------|----------|
| Dominio | `domain/ia/Aviso.java` | Un aviso: nivel, a qué roles va y, para el mesero, de quién es la mesa |
| Dominio | `domain/ia/TiemposPreparacion.java` | Tiempo normal de cada plato (mediana de los pedidos reales) |
| Dominio | `domain/ia/GeneradorAvisos.java` | Todas las reglas de la tabla de arriba |
| Aplicación | `application/port/in/ConsultarAvisosUseCase`, `application/service/AvisosService` | Reúne los datos y filtra por rol (al mesero no se le calcula el inventario) |
| Infraestructura | `infrastructure/adapter/in/web/AvisosController` | `GET /api/avisos` (todos los roles) |
| Frontend | `components/avisos-campana/*`, `core/services/avisos.service.ts`, `core/util/avisos.ts` | La campana y su panel |

### Pruebas

| Prueba | Qué comprueba |
|--------|---------------|
| `AvisosDominioTest` (6) | Mediana del tiempo de cada plato, demora atención / urgente, listo y sin cobrar, inventario, demanda y quién ve qué |
| `AvisosServiceTest` (3) | Cada rol recibe lo suyo, el mesero solo sus mesas y sin análisis de inventario, el aviso se va solo al entregar |
| `AvisosIntegracionTest` (2) | De punta a punta con H2: un pedido con una hora en cocina avisa a cocina, a su mesero y al admin, no a otro mesero ni a inventario; sin sesión, 401 |
| `avisos.test.ts` (7, Jest) | Número de la campana, vistos, enlaces por rol, "hace cuánto", guardado en el navegador |

### Cómo probarlo

1. Entrar como **mesero** y registrar un pedido.
2. Esperar más de 15 minutos sin que cocina lo marque Listo (o más de su tiempo normal si ese plato ya tiene
   historial). Para la demostración se puede crear el pedido antes de empezar.
3. Entrar como **cocina**: la campana muestra "Mesa X · pedido #N demorado".
4. Marcarlo Listo y esperar 3 minutos sin entregarlo: al **mesero** le aparece "listo para llevar".
5. Como **inventario**, la campana muestra los insumos que se están acabando.

---

## 3. IA externa: Google Gemini

Para validar el uso de una **API externa de IA**, RestoFlow AI se conectó a **Google Gemini** (plan gratuito de
Google AI Studio), con el mismo patrón que Open Food Facts.

### Las tres funciones

| # | Función | Quién | Dónde |
|---|---------|-------|-------|
| 1 | **Resumen de la campana**: qué atender primero, en 2 o 3 frases, según los avisos de su rol | Todos los roles | Al abrir la campana |
| 2 | **Pregúntale a RestoFlow AI**: preguntas libres respondidas con los datos reales del restaurante | Solo ADMIN | Pantalla RestoFlow AI |
| 3 | **Recomendaciones redactadas por la IA**: Gemini analiza ventas, insumos y predicción y propone 3 a 5 acciones | ADMIN e INVENTARIO | Pantalla RestoFlow AI, tarjeta "Recomendaciones" |

### Decisiones (para la sustentación)

- **Las reglas deciden, la IA explica.** Los avisos y los números los calcula RestoFlow (exactos y probados). A
  Gemini solo se le entregan esos datos para que los redacte, priorice y responda preguntas. Se le ordena no
  inventar y decir cuándo falta un dato.
- **Plan B (tolerancia a fallas).** Si Gemini no responde, se acaba el cupo gratuito o no hay clave:
  - la campana muestra un resumen armado con reglas,
  - las recomendaciones vuelven a las calculadas con reglas,
  - el chat responde 503 con un mensaje claro.
  La pantalla siempre indica el origen: "Redactado por Gemini" o "Calculado con reglas".
- **Privacidad.** Solo se envían datos del negocio (platos, ventas, insumos, avisos). Nunca nombres, correos,
  contraseñas ni tokens. En el log queda qué función se usó y cuánto tardó (`evento=CONSULTA_IA`), nunca la
  pregunta ni la respuesta.
- **Modelo principal y de respaldo.** Se usa `gemini-3.5-flash-lite`, el más rápido del plan gratuito (responde en
  cerca de 1 segundo; para redactar con datos que ya se le dan no hace falta un modelo más grande). Si está
  saturado (503), sin cupo (429), retirado (404) o tarda más de 15 s, se prueba automáticamente
  `gemini-3.8-flash` (`GEMINI_MODELOS_RESPALDO`). Solo si todos fallan se usa el plan B. Se decidió así después de
  ver en las pruebas que el modelo Flash del plan gratuito a veces responde "high demand" o tarda más de 20 s.
- **Cupo gratuito.** El resumen se guarda 5 minutos (mientras los avisos no cambien) y las recomendaciones 30
  minutos. Sin avisos no se llama a Gemini.
- **Sin transacción abierta mientras la IA piensa.** El servicio no es `@Transactional`: cada consulta de datos
  abre y cierra su conexión, y la base de datos no queda ocupada los segundos que tarda Gemini.
- **La clave es un secreto (factor III).** Va en la variable de entorno `GEMINI_API_KEY` (`.env` o
  `application-local.yml`), viaja en el encabezado `x-goog-api-key` (nunca en la URL) y no aparece en los logs.
- **Se puede cambiar de IA (SOLID - O y D).** La aplicación solo conoce el puerto `AsistenteIAPort`. Para usar
  otra IA (Groq, OpenAI, Claude) se escribe otro adaptador; nada más cambia.

### Dónde está

| Capa | Archivo | Qué hace |
|------|---------|----------|
| Dominio | `domain/ia/InstruccionesIA.java` | Instrucciones para Gemini, datos que se le entregan, lectura de su respuesta y plan B |
| Dominio | `domain/ia/RespuestaIA.java` | Texto + origen (GEMINI o REGLAS) |
| Dominio | `domain/exception/AsistenteIANoDisponibleException.java` | 503 con mensaje claro |
| Aplicación | `application/port/out/AsistenteIAPort.java` | Puerto hacia cualquier IA externa |
| Aplicación | `ResumirAvisosIAUseCase`, `ConsultarAsistenteIAUseCase`, `AsistenteIAService` | Las tres funciones, con caché y plan B |
| Infraestructura | `adapter/out/externo/GeminiAdapter.java` | `POST /v1beta/models/{modelo}:generateContent` |
| Infraestructura | `config/ApisExternasConfig.java`, `RestoFlowProperties.Gemini` | Cliente HTTP, clave, modelos y tiempo máximo (15 s por modelo) |
| Infraestructura | `AvisosController` (`GET /api/avisos/resumen`), `AsistenteIAController` (`POST /api/restoflow-ai/preguntar`, `GET /api/restoflow-ai/recomendaciones-ia`) | API |
| Frontend | `components/avisos-campana/*`, `pages/restoflow-ai/*`, `core/util/asistente-ia.ts` | Resumen en la campana, chat y origen de las recomendaciones |

### Pruebas (nunca llaman a internet ni gastan cupo)

| Prueba | Qué comprueba |
|--------|---------------|
| `GeminiAdapterTest` (6) | Con un servidor simulado: el JSON que se envía, la clave en el encabezado, que ignora el "razonamiento" interno, modelo de respaldo cuando el principal está saturado o la respuesta se corta, 403 sin reintento, 429 / respuesta vacía → 503, sin clave no llama |
| `InstruccionesIATest` (5) | Lectura de las recomendaciones (y de líneas mal formadas), datos que se envían (sin correos), plan B, validación de la pregunta, limpieza del markdown |
| `AsistenteIAServiceTest` (4) | Caché de 5 y 30 minutos, plan B cuando Gemini falla, la pregunta lleva los datos del restaurante, 503 sin clave |
| `AsistenteIAIntegracionTest` (1) | De punta a punta sin clave: la campana y las recomendaciones responden con reglas, el chat da 503, el inventario recibe 403 en el chat |
| `asistente-ia.test.ts` (4, Jest) | Origen del texto, cuándo se puede enviar, conversación, contador |

### Cómo activarlo

1. Entrar a **https://aistudio.google.com** con una cuenta de Google → **Get API key** → **Create API key**.
2. Pegarla **solo** en su archivo privado (nunca en GitHub ni en un chat):
   - modo local, en `backend/src/main/resources/application-local.yml`:
     ```yaml
     restoflow:
       apis-externas:
         gemini:
           api-key: SU_CLAVE
     ```
   - Docker, en el `.env`: `GEMINI_API_KEY=SU_CLAVE`
3. Reiniciar el backend. En el log aparece `Gemini (gemini-3.5-flash-lite) respondió en ... ms` cuando se usa.
4. Si Google cambia el modelo gratuito, se cambia con `GEMINI_MODELO` sin tocar el código.

---

## 4. Validar la lógica de la aplicación

### Regla nueva: una mesa ocupada no recibe otro pedido

Revisando la lógica del mesero se encontró que se podían registrar dos pedidos abiertos en la misma mesa. Ahora:

- Una mesa está **ocupada** mientras tenga un pedido **abierto** (pendiente, en preparación, listo o entregado
  sin cobrar).
- Si el mesero escribe una mesa ocupada, la pantalla lo avisa **mientras escribe** y no deja enviar; el backend
  también lo rechaza (409): *"La mesa 5 está ocupada con el pedido #275. Se libera cuando ese pedido se cobre o se
  cancele, o cuando se cambie a otra mesa."*
- La mesa se **libera** al **cobrar** (registrar la venta) o al **cancelar** el pedido.
- **Cambiar mesa:** si el cliente se cambia de lugar, el mesero usa "Cambiar mesa" en el pedido. Solo a una mesa
  libre, en cualquier estado de cocina mientras el pedido esté abierto; la mesa anterior queda libre. Queda en la
  auditoría (`evento=MESA_CAMBIADA`).
- Debajo del campo Mesa se ven las mesas ocupadas **con el mesero que las atiende** ("5 Laura", "7 Pedro").
- **Ya enviado a cocina, lo único que se puede cambiar es la mesa.** Mientras está Pendiente se puede editar o
  cancelar; desde En preparación los productos quedan fijos (la cocina ya los está haciendo) y solo aparece
  "Cambiar mesa" (y "Registrar venta" cuando se entrega). El backend responde 409 con ese mismo mensaje si alguien
  intenta modificarlo.

**El número del pedido solo lo ve el administrador.** El mesero y la cocina identifican cada pedido por su mesa
(basta, porque una mesa tiene un solo pedido abierto): "Pedido de la mesa 5 enviado a cocina", la comanda dice
"Mesa 5", los avisos dicen "Mesa 5 · pedido demorado" y el mensaje de mesa ocupada dice quién la atiende, sin el
número. El administrador sigue viendo el # en Pedidos, Cocina y Reportes.

**Numeración por día:** los pedidos se numeran **#1, #2, #3... y la cuenta vuelve a empezar cada día** (el primer
pedido de mañana es otra vez el #1). Los cancelados también cuentan, para que no haya números repetidos ni huecos
que expliquen. Es el número que ve el administrador en Pedidos, Cocina y Reportes (junto a la fecha). Por dentro, la
base de datos sigue usando el `id_pedido` de siempre, que no se reinicia (las ventas, el inventario y la auditoría
se relacionan con él). Columna nueva `numero_dia` (script `backend/database/04_numero_pedido_dia.sql`).

**Registrar la venta con método de pago:** al cobrar se abre una ventana (centrada, angosta, con los colores de
RestoFlow) con la mesa, el total y dos opciones: **Efectivo** o **Transferencia**. "Aceptar" se activa solo cuando
se elige el método; "Cancelar" o la tecla Esc cierran sin cobrar. El método queda guardado en el pedido (columna
`metodo_pago`), en la auditoría (`evento=VENTA_REGISTRADA ... pago=TRANSFERENCIA`) y en Reportes; el API rechaza
cualquier otro método (400).

**Solo el administrador registra la venta; el mesero pasa la cuenta:** el mesero ya no cobra. En un pedido
entregado su botón dice **"Pasar la cuenta"** y abre la misma ventana con los productos consumidos (precio por
unidad, cantidad y subtotal), el **total a pagar** y el botón **"Imprimir factura"**, que imprime un comprobante
angosto (tipo tirilla de 72 mm) con el nombre del restaurante, la mesa, el mesero, la fecha y el detalle, para
entregarlo al cliente. El administrador ve la misma cuenta y además elige el método de pago y pulsa "Aceptar".
¿Por qué así? Separa funciones (quien atiende la mesa no es quien recibe el dinero), como en una caja real, y deja
un solo responsable del cuadre del día. La regla está en el backend, no solo en la pantalla:
`PATCH /api/pedidos/{id}/pagar` es solo `ADMIN` en `SecurityConfig` (un mesero recibe 403 aunque llame al API a
mano; lo prueban `PedidoFlujoIntegracionTest` y `ReglasDeNegocioIntegracionTest`). El aviso "sin cobrar" ahora le
dice al mesero "Pase la cuenta a la mesa". El comprobante aclara que no reemplaza la factura electrónica.

**Cierre del día (Dashboard del administrador):** pedidos registrados hoy, cobrados, abiertos y cancelados, el
total vendido, **cuánto entró en efectivo y cuánto por transferencia** (para cuadrar la caja) y una tabla con los
pedidos cobrados y lo vendido por cada mesero. Si quedan pedidos sin cobrar, lo
avisa para revisarlos antes de cerrar.

| Capa | Archivo | Qué cambió |
|------|---------|------------|
| Dominio | `Pedido.java` | `ocupaMesa()` y `cambiarMesa()` (solo pedidos abiertos, mesa válida y distinta) |
| Dominio | `MesaOcupadaException.java` | 409 con el pedido que ocupa la mesa |
| Aplicación | `GestionarPedidosService` | Revisa la mesa al registrar y al cambiar; `mesasOcupadas()` |
| Infraestructura | `PedidoController` | `PATCH /api/pedidos/{id}/mesa` y `GET /api/pedidos/mesas-ocupadas` (MESERO y ADMIN; la cocina solo consulta) |
| Frontend | `pages/pedidos/*`, `core/util/mesas.ts` | Aviso al escribir, mesas ocupadas con su mesero y botón "Cambiar mesa" |
| Frontend | `pages/pedidos/*`, `pages/cocina/*` | El # del pedido solo para el administrador |
| Aplicación + frontend | `ConsultarPanelService` (`pedidosRegistrados`, `porMesero`), `pages/inicio/*` | Cierre del día |
| Seguridad + frontend | `SecurityConfig`, `pages/pedidos/*`, `styles.scss` (`@media print`) | Solo ADMIN registra la venta; el mesero pasa la cuenta e imprime la factura |

### Pruebas de las reglas del negocio

`ReglasDeNegocioIntegracionTest` recorre las reglas por el API real (seguridad, casos de uso, dominio y base de
datos H2), con los roles de verdad:

| Prueba | Regla |
|--------|-------|
| Mesa ocupada hasta cobrar | 409 con el pedido que la ocupa; sigue ocupada aunque esté entregado; se libera al cobrar |
| Mesa libre al cancelar | Cancelar libera la mesa |
| Cambiar de mesa | Solo a una mesa libre; la anterior se libera; la cocina recibe 403; mesa 0 → 400 |
| Estados en orden | No se cobra lo no entregado, no se cancela ni modifica lo que se prepara, no se salta de estado, no se cobra dos veces, un pedido cobrado no se mueve |
| Datos inválidos | Mesa 0 o 1000, cantidad 0 → 400; producto inexistente → 404; los intentos fallidos no ocupan la mesa |

Además: `PedidoTest` (dominio) y `GestionarPedidosServiceTest` (+4: mesa ocupada, se libera al cobrar o cancelar,
cambiar de mesa y sus reglas) y `mesas.test.ts` (Jest, 4).

Estas pruebas se suman a las de punta a punta que ya existían: `PedidoFlujoIntegracionTest` (pedido completo con
cada rol), `InventarioFlujoIntegracionTest` (insumo → receta → venta → descuento → alerta → panel → historial),
`AvisosIntegracionTest`, `AsistenteIAIntegracionTest` y `RegistroActividadIntegracionTest`.

## 5. Cobertura del frontend sobre el 80 % (SonarQube)

**Retroalimentación del comité:** la cobertura del frontend en SonarQube debe superar el 80 %.

### Por qué estaba en 63,5 %

SonarQube mide **todo** el código TypeScript de `frontend/src`, pero Jest solo medía la lógica pura de `core/util`
(unas 280 líneas, casi al 100 %). Las pantallas (`pages/*`), la campana, los servicios, los guards y el interceptor
no tenían pruebas, así que contaban como 0 % y bajaban el total. Además el Quality Gate pedía **0 issues nuevos** y
había 25 abiertos.

### Qué se hizo

1. **Pruebas de las pantallas con el mismo Jest** (no se agregó otra herramienta). Cada pantalla se crea como una
   clase dentro de un inyector de Angular con **servicios falsos** (`src/testing/angular.ts`): sin navegador, sin
   backend y en milisegundos. Se prueba su lógica real: cargar datos, armar el carrito, validar formularios,
   registrar la venta, cambiar de mesa, mostrar errores del API, temporizadores (con `jest.useFakeTimers`), etc.
2. **Configuración de Jest** (`jest.config.js`): `@swc/jest` ahora entiende los decoradores de Angular
   (`@Component`, `@HostListener`), traduce las librerías de Angular (`.mjs`) a CommonJS y carga el compilador JIT
   antes de cada archivo (`src/testing/preparar-jest.ts`). La cobertura se mide sobre `src/app/**` (lo mismo que
   SonarQube), sin los archivos que solo arrancan Angular ni los modelos (interfaces sin código).
3. **SonarQube** (`sonar-project.properties`): `src/testing/**` cuenta como código de pruebas, no de la aplicación.
4. **Los 25 issues**, sin cambiar textos ni colores:
   - Accesibilidad (S6819): elementos nativos en vez de roles ARIA: `<output>` en lugar de `role="status"`,
     `<dialog open>` en lugar de `role="dialog"` (campana y ventana de cobro), `<ul>/<li>` para las mesas ocupadas,
     `<fieldset>` para grupos de botones, `<figure>` para las gráficas y botones de radio reales
     (`<input type="radio">`) para el método de pago. En `styles.scss` se quitan los estilos propios del navegador de
     esos elementos para que se vean igual que antes.
   - S7927: el filtro de insumos de Reportes tiene una etiqueta `<label>` (oculta a la vista) en vez de `aria-label`.
   - S1135: un comentario decía "Todo en orden" y SonarQube lo leía como "TODO"; se reescribió.
   - S6582 (encadenamiento opcional `?.`), S5906 (`toHaveLength`) y S4666 (selector `body` repetido en `styles.scss`).

### Pruebas nuevas (120)

| Archivo | Qué prueba |
|---------|-----------|
| `pages/pedidos/pedidos.test.ts` | Carrito, mesa ocupada, registrar y editar, cancelar con confirmación, cambiar de mesa, cobro solo del admin, imprimir, filtros, actualización automática |
| `pages/inventario/inventario.test.ts` | Entradas, salidas (motivo obligatorio), ajustes, crear y editar insumos, código de barras (Open Food Facts), alertas |
| `pages/productos/productos.test.ts` | Crear y editar productos, disponibilidad, categorías, receta |
| `pages/restoflow-ai/restoflow-ai.test.ts` | Análisis, recomendaciones de Gemini o de reglas, filtros, chat, alertas anticipadas |
| `pages/inicio`, `reportes`, `cocina`, `usuarios`, `login`, `recuperar-contrasena` | Lo que ve cada rol, consultas, validaciones y mensajes de error |
| `components/avisos-campana`, `logo`, `panel-marca`, `layout/estructura` | Campana (cada minuto, resumen de Gemini, cerrar con Escape o clic fuera), menú por rol |
| `core/services/*.test.ts` | Cada servicio llama a la URL y con los datos correctos; sesión de `AuthService` |
| `core/guards`, `core/interceptors` | Redirección al login o al inicio; token solo para nuestro API; 401 cierra la sesión |

### Cómo comprobarlo

```powershell
cd frontend
npm run test:jest:cobertura      # 229 pruebas y la tabla de cobertura (debe superar 80 %)
npm run test:jest:tipos          # revisa los tipos de las pruebas
cd ..
powershell -ExecutionPolicy Bypass -File .\analizar-calidad.ps1 -SoloFrontend
```

## 6. Ajustes del frontend antes de la entrega

### 6.1 Una sola herramienta de pruebas

Se quitaron los `*.spec.ts` que Angular CLI genera solos ("should create"), `tsconfig.spec.json`, el `test` de
`angular.json` y las librerías Vitest y jsdom. Todas las pruebas del frontend son de Jest y `npm test` las ejecuta.

### 6.2 Ventanas propias para "Cancelar pedido" y "Cambiar mesa"

Antes usaban los cuadros del navegador (`confirm` y `prompt`): grises, distintos en cada navegador y sin explicar
nada. Ahora son ventanas como la del cobro, con los colores de RestoFlow. "Cancelar pedido" explica que la mesa
queda libre y que no se puede deshacer; "Cambiar de mesa" muestra el error dentro de la misma ventana si la mesa
está ocupada, es la misma o no es válida. Se cierran con "Volver", con un clic afuera o con Escape.

### 6.3 Número del día en el historial de Inventario

Las salidas por venta mostraban el id interno del pedido ("pedido #275"). Ahora cada movimiento trae el número del
pedido en su día y la mesa (`pedidoNumeroDelDia`, `pedidoMesa`), y el motivo de las ventas nuevas dice
"Venta del pedido #4 (mesa 3)". El administrador ve "Venta · pedido #4 · mesa 3"; el personal de inventario,
"Venta · mesa 3" (el número del pedido es solo del administrador, igual que en Pedidos y Cocina).

### 6.4 Adición: el cliente pide algo más con el pedido ya en cocina

**Regla (decidida con el equipo):** los productos se suman **al mismo pedido y a su cuenta**, marcados como
"Adición". Así la mesa sigue con un solo pedido y una sola cuenta, y el cierre del día cuenta una sola venta.

| Estado del pedido | ¿Se puede agregar? | Qué pasa |
|---|---|---|
| Pendiente | No: se usa "Editar" | Todavía no se prepara, así que se cambia el pedido completo |
| En preparación | Sí | La cocina ve las líneas nuevas marcadas "Adición" |
| Listo o Entregado | Sí | Vuelve a **En preparación**: la cocina prepara la adición y la entrega antes de cobrar |
| Pagado o Cancelado | No (409) | El pedido ya está cerrado |

- **Backend:** `Pedido.agregarAdicion` (la regla está en el dominio), `DetallePedido.adicion`,
  `Pedido.fechaAdicion`, `POST /api/pedidos/{id}/adiciones` (MESERO y ADMIN) y el evento `ADICION_AGREGADA` en
  los logs. Script `backend/database/05_adiciones.sql` (columnas `detalle_pedidos.adicion` y
  `pedidos.fecha_adicion`), montado en `docker-compose.yml`.
- **Demora en cocina:** con una adición, la demora se mide desde la hora de la adición (no desde el pedido
  original), y esos pedidos no se usan para aprender los tiempos normales de preparación (su hora de Listo incluye
  lo que se pidió después).
- **Frontend:** botón "Agregar productos" en los pedidos que ya están en cocina; el formulario pasa a "Adición al
  pedido de la mesa N" con la mesa fija y el carrito solo con lo nuevo. En el detalle del pedido y en las comandas
  de Cocina, las líneas nuevas llevan la etiqueta "Adición", y la comanda cuenta los minutos desde la adición.
- **Pruebas:** `PedidoTest` (3 reglas), `GestionarPedidosServiceTest.adicion`, `ReglasDeNegocioIntegracionTest.adicion`
  (API real: 409 si está Pendiente, 403 para cocina, vuelve a cocina, no se cobra hasta entregar) y Jest
  (`pedidos.test.ts`, `cocina.test.ts`, `servicios-api.test.ts`).

### 6.5 Mi cuenta (antes "Configuración · Pronto")

El menú tenía "Configuración" marcado como "Pronto", algo sin terminar a la vista. Ahora es **Mi cuenta**, para todos
los roles: muestra el nombre, el correo y el rol del usuario, y permite **cambiar la contraseña escribiendo la actual**
(así, si alguien encuentra la sesión abierta, no puede cambiarla sin saberla). Es distinto de "Recuperar contraseña",
que es para quien la olvidó y usa un código enviado al correo.

- **Backend:** caso de uso `CambiarContrasenaUseCase` / `CambiarContrasenaService` y `PATCH /api/auth/contrasena`
  (con sesión). La nueva sigue las mismas reglas de siempre (`Usuario.validarContrasenaSegura`: 8 caracteres, letras y
  números) y debe ser distinta de la actual. Si la actual no es correcta responde **400, no 401**: un 401 haría que el
  frontend cerrara la sesión, y el usuario solo se equivocó al escribirla. Eventos `CONTRASENA_CAMBIADA` y
  `CAMBIO_CONTRASENA_RECHAZADO` en los logs (sin la contraseña).
- **Frontend:** página `pages/mi-cuenta` (ruta `/mi-cuenta`, solo exige sesión) con los datos y el formulario
  (contraseña actual, nueva y confirmación). Se quitó del menú el estado "Pronto", que ya no tenía uso.
- **Pruebas:** `CambiarContrasenaServiceTest` (4), `CuentaIntegracionTest` (API real: sin sesión 401, actual
  equivocada 400, la anterior deja de servir y la nueva sí) y Jest (`mi-cuenta.test.ts`, `auth.service.test.ts`,
  `estructura.test.ts`).

### 6.6 Celular y tableta

Se revisó la aplicación real (backend y frontend encendidos) en tamaño celular (375 px) y tableta (768 px), con los
roles de mesero y administrador, midiendo en cada pantalla si algo se salía del ancho.

| Problema encontrado | Corrección |
|---|---|
| Pedidos: la tabla de pedidos era más ancha que la pantalla; los botones (pasar la cuenta, agregar productos, cambiar mesa) quedaban escondidos a la derecha | Hasta 900 px cada pedido se muestra como **tarjeta**: mesa y estado, luego hora, mesero y total, y los botones siempre visibles |
| Pedidos: en el celular el menú iba en una columna y había que bajar mucho para llegar al pedido | Menú en **dos columnas** en el celular; los filtros de la lista bajan a otra línea |
| Login: el panel de marca ocupaba toda la primera pantalla. La regla que lo ocultaba nunca funcionó: `:host { display: flex }` del componente es más específico que `app-panel-marca { display: none }` | En celular y tableta se ve una **franja con el logo** y el formulario de inmediato |
| Inventario, Productos y Usuarios: toda la página se desplazaba de lado | Dos causas: (1) columnas `1fr` de CSS Grid no se encogen por debajo del ancho de la tabla → `minmax(0, 1fr)`; (2) los textos ocultos para lectores de pantalla (`.rf-oculto-visual`, `position: absolute`) se salían de la caja de la tabla → las cajas con desplazamiento ahora son `position: relative` |
| Compilación de producción fallaba (`pedidos.scss` superó el presupuesto de 8 kB de Angular) | Presupuestos en `angular.json`: estilos por componente 6 kB (aviso) / 12 kB (error); paquete inicial 600 kB (aviso). Sin esto, `ng build` para Docker o AWS habría fallado; `npm start` no lo revisa |

Resultado: las 9 pantallas caben en el ancho del celular y de la tableta sin desplazamiento lateral de la página
(las tablas grandes de Inventario, Reportes y RestoFlow AI se desplazan dentro de su propia caja). Se revisaron
también la ventana de la cuenta, la campana y Mi cuenta en el celular.


### 6.7 Desactivar y reactivar cuentas del personal

En "Cuentas del personal" el administrador solo podía crear cuentas; no tenía cómo quitarle el acceso a alguien que
ya no trabaja en el restaurante. Ahora cada fila tiene el botón **Desactivar** (o **Reactivar** si está inactiva),
con una ventana de confirmación del mismo estilo de las de Pedidos.

**Por qué desactivar y no eliminar:** los pedidos, las ventas por mesero y los movimientos de inventario guardan
quién los hizo. Borrar la cuenta rompería ese historial (o la base de datos lo impediría por las llaves foráneas) y
los reportes perderían el nombre del mesero. Desactivar corta el acceso de inmediato, conserva el historial y se
puede deshacer. Es la práctica habitual en sistemas de punto de venta.

| Regla | Dónde se aplica |
|---|---|
| Solo el administrador cambia el estado de una cuenta | `SecurityConfig` (`/api/usuarios/**` exige ADMIN) |
| El administrador no puede desactivar su propia cuenta (409). Como quien lo hace es un administrador activo, siempre queda al menos uno con acceso | `GestionarCuentasService.cambiarEstado` |
| Una cuenta desactivada no inicia sesión | Ya existía: `Usuario.validarPuedeIngresar()` |
| **Nuevo:** si tenía una sesión abierta, su token deja de servir de inmediato (401 y el frontend la saca al login), sin esperar las 8 horas de vigencia del token | `JwtAuthenticationFilter` consulta la cuenta por su id (llave primaria, consulta muy rápida) en cada petición |

- **API:** `PATCH /api/usuarios/{id}/estado` con `{"activa": false}` o `{"activa": true}`. Eventos
  `CUENTA_DESACTIVADA` y `CUENTA_REACTIVADA` en `logs/auditoria.log`, con el id del administrador que lo hizo.
- **Frontend:** columna de acciones en `pages/usuarios`; en la fila propia dice "Tu cuenta" en lugar del botón.
  Las cuentas inactivas se ven atenuadas con la etiqueta "Inactiva".
- **Lista limpia:** encima de la tabla hay un filtro **Activas · Inactivas · Todas** (las mismas píldoras de
  Reportes, con el número de cuentas en cada una). Por defecto muestra solo las activas; las desactivadas no se
  pierden, quedan a un clic para reactivarlas.
- **Pruebas:** `GestionarCuentasServiceTest` (+3: desactivar y reactivar, no a sí mismo, cuenta inexistente),
  `CuentaIntegracionTest` (+2 por el API real: el mesero no puede, cuerpo vacío 400, cuenta inexistente 404, el token
  abierto pasa a 401, al iniciar sesión recibe 403 "La cuenta está desactivada", sigue en la lista, al reactivarla entra con la misma contraseña; el
  administrador no puede desactivarse) y Jest (`usuarios.test.ts` +6, `servicios-api.test.ts`).

### 6.8 Error de concurrencia encontrado con la prueba de carga (RES-45)

La primera prueba de carga (RES-56, 30 usuarios durante 5 minutos, RestoFlow en Docker en el PC) **no cumplió** los
3 s. La mayor parte del tiempo respondía en menos de 60 ms (P95), pero en 6 momentos todo el sistema se detuvo de 5 a
11 segundos y 18 cobros terminaron con error 500. Los registros del backend lo explicaron: **23 deadlocks de SQL
Server** (error 1205, *"chosen as the deadlock victim"*). 21 ocurrieron al cobrar, en el descuento de inventario, y 2
al leer las comandas de Cocina.

**Causa (dos problemas que se sumaban):**

1. **El descuento de inventario no reservaba el insumo.** Cada cobro hacía *leer stock → restar → guardar*. Con dos
   cobros simultáneos del mismo insumo, los dos leían el mismo valor: una de las restas **se perdía** (el inventario
   quedaba con más de lo real, sin ningún error visible) o SQL Server cancelaba uno de los dos por deadlock.
2. **En SQL Server, por defecto, quien lee espera a quien escribe.** La consulta de alertas pendientes que se hace
   después de cada descuento recorría la tabla de alertas y quedaba esperando los bloqueos de otro cobro, que a su vez
   esperaba los suyos (espera en círculo). Mientras tanto, las pantallas de Cocina, Pedidos y el Dashboard también
   esperaban: por eso se detenía todo a la vez.

**Corrección:**

| Cambio | Dónde | Efecto |
|---|---|---|
| Reservar los insumos antes de cambiar su stock (bloqueo pesimista de escritura, en orden de id) y volver a leerlos después del bloqueo | `IngredienteRepositoryPort.bloquearPorIds` (implementado en `IngredientePersistenceAdapter` con `EntityManager.find(..., PESSIMISTIC_WRITE)` + `refresh`), `DescontarInventarioService` y las entradas, salidas, ajustes y cambios de `GestionarInventarioService` | Dos ventas del mismo insumo se hacen una después de la otra y la segunda lee el stock ya descontado. Al reservar siempre en el mismo orden, no hay esperas en círculo |
| Lecturas sin bloqueo (`READ_COMMITTED_SNAPSHOT`) | Script nuevo `backend/database/06_lecturas_sin_bloqueo.sql`, que Docker Compose ejecuta al iniciar | Las pantallas leen la última versión confirmada sin esperar a los cobros. Es la configuración que Microsoft recomienda para aplicaciones web |
| Índice `ix_alertas_ingrediente_atendida` | El mismo script `06` | La consulta de alertas pendientes de un insumo ya no recorre toda la tabla |

**La primera versión de la corrección no bastó, y la prueba nueva lo detectó.** Solo con el bloqueo
(`@Lock(PESSIMISTIC_WRITE)` en una consulta), los 10 cobros simultáneos respondían 200, pero el stock bajaba 1 en vez de 10. Al leer la receta,
Hibernate ya había cargado el insumo en la memoria de la transacción, antes del bloqueo. Cuando la consulta con
bloqueo traía el valor nuevo, Hibernate conservaba la copia vieja: dentro de una transacción no reemplaza un objeto
que ya cargó. Para descartar la base de datos se probó H2 2.3.232 directamente con JDBC: con `FOR UPDATE` el stock
quedó bien (90 de 90). Por eso ahora, después del bloqueo, se hace `refresh` para leer el valor confirmado.

**Por qué un bloqueo pesimista y no uno optimista (`@Version`):** con el optimista, el segundo cobro fallaría y habría
que reintentarlo. En la hora pico los cobros del mismo insumo son frecuentes, y reservar el insumo unos milisegundos
es más simple y no pierde ninguna venta.

**Pruebas:**
- `DescontarInventarioServiceTest` (+1) y `GestionarInventarioServiceTest` (+1): toda operación que cambia un insumo lo
  reserva primero; consultarlo no lo reserva.
- `ConcurrenciaInventarioIntegracionTest` (nueva): 10 cobros **al mismo tiempo**, en 10 hilos, de pedidos con el mismo
  insumo. Todos responden 200 y el stock baja exactamente 10.
- Prueba de carga repetida después de la corrección: resultados en `docs/pruebas/carga-y-estres.md`.

### 6.9 El sistema no se recuperaba después del pico de la prueba de estrés (RES-59)

La prueba de estrés en el servidor de AWS (RES-56 y RES-57, 10 de noviembre) mostró dos cosas:

| Usuarios a la vez | Qué pasó |
|---:|---|
| hasta ~180 | Mediana de ~110 ms y 0 errores |
| 180 a 300 | Se degrada: respuestas de 1 a 6 s, casi sin errores |
| desde ~330 | El 100 % de las peticiones espera 30 s y JMeter las corta (`Read timed out`) |
| 5 (fase de recuperación) | **Siguió sin responder.** Cinco minutos después de terminar, el Dashboard todavía tardaba 55 s |

Que el sistema se sature con 6 veces la carga normal es esperado: para eso es la prueba. Lo que no estaba bien era
que **no se recuperara** cuando la carga bajó.

**Diagnóstico (en el servidor, con `docker stats`, `free -h` y los registros del backend):**

- **No fue la memoria:** el backend usaba 1,2 GB y quedaban 4,9 GB libres. Ningún contenedor se reinició.
- **No fue el inventario en cero:** en la prueba de carga los insumos ya estaban en 0 y hubo 1 381 cobros sin errores.
  Además fallaron sobre todo el inicio de sesión (67 %) y el menú (71 %), que no usan inventario, y cobrar solo
  falló en el 1,9 %.
- **El Dashboard ocupaba la base de datos.** `GET /api/panel/resumen` carga todos los pedidos del día para calcular
  ventas, ventas por hora, por mesero y los más vendidos. Cada venta lo vuelve un poco más pesado. Con ~2 600 ventas
  en el día y 40 administradores pidiéndolo cada 5 s, ocupaba las 10 conexiones a SQL Server (el valor por defecto
  de HikariCP). Las demás peticiones, incluso el filtro del token, que revisa si la cuenta sigue activa, esperaban
  turno hasta 30 s.
- **Nada se cancelaba.** Cuando JMeter se rendía a los 30 s, el backend seguía trabajando en esa petición (en los
  registros aparecía `Broken pipe` al responder a un cliente que ya no estaba). Se formó una fila de trabajo que
  nadie esperaba, y por eso los 5 usuarios de la recuperación seguían esperando.
- Aparte, el chequeo de salud del frontend preguntaba a `localhost`. En la imagen Alpine eso apunta a la dirección
  IPv6 y Nginx escucha en IPv4, así que Docker mostraba el frontend como `unhealthy` aunque funcionaba.

**Corrección:**

| Cambio | Dónde | Efecto |
|---|---|---|
| El resumen del Dashboard se calcula como máximo una vez cada 5 s y se comparte entre todos los que lo piden. Si varios llegan cuando venció, solo uno lo calcula | `ConsultarPanelEnCacheService` (nuevo), que envuelve a `ConsultarPanelService` (patrón decorador). Se configura en `BeanConfig` con `restoflow.panel.vigencia-segundos` | 40 administradores = 1 cálculo cada 5 s, no 40. El Dashboard del frontend se actualiza cada 10 s, así que las cifras siguen al día |
| Fallar rápido: máximo 5 s esperando una conexión libre y máximo 10 s por transacción | `application.yml`: `spring.datasource.hikari.connection-timeout` y `spring.transaction.default-timeout` | Una petición que no consigue capacidad sale de la fila en vez de acumularse. Las consultas se cancelan en SQL Server al pasar el tiempo |
| Si el sistema está saturado se responde **503 "El sistema está ocupado"** con `Retry-After: 5` | `GlobalExceptionHandler` y `JwtAuthenticationFilter` (el filtro corre antes que los controladores) | El cliente recibe una respuesta clara ("intente de nuevo") en vez de un error 500 o de una espera sin fin |
| Chequeo de salud del frontend con `127.0.0.1` | `frontend/Dockerfile` | Docker muestra el estado real del frontend |

**Por qué no simplemente más conexiones:** el servidor tiene 2 CPU para SQL Server. Más conexiones harían más
consultas al mismo tiempo, cada una más lenta, y el problema volvería a aparecer con un poco más de usuarios. Lo que
evita el colapso es no repetir trabajo (el caché) y no aceptar más trabajo del que se puede hacer (fallar rápido).
El tamaño de la reserva quedó configurable (`DB_POOL_MAXIMO`) por si se cambia el servidor.

**Pruebas:**
- `ConsultarPanelEnCacheServiceTest` (nueva, 5 pruebas): reutiliza el resumen durante la vigencia, lo recalcula al
  vencer, no confunde días, con vigencia 0 siempre recalcula, y **40 administradores al mismo tiempo producen un solo
  cálculo**.
- `SistemaOcupadoTest` (nueva, 4 pruebas): sin conexión libre o con una consulta vencida, el controlador responde 503
  con `Retry-After`. El filtro del token también responde 503 y no deja pasar la petición, y sin saturación sigue
  autenticando igual que antes.
- En las pruebas de integración la vigencia es 0, porque leen el Dashboard justo después de cobrar.
- Carga y estrés repetidos en AWS después de la corrección (detalle en `docs/pruebas/carga-y-estres.md`):

| | Antes | Después |
|---|---|---|
| Carga, 100 usuarios | 0 errores, P95 234 ms | 0 errores, P95 284 ms (con el triple de pedidos en el día) |
| Estrés: peticiones atendidas | 21 962 | 43 829 |
| Estrés: ventas completas | 1 213 | 2 810 |
| Estrés: errores | 31,2 %, casi todos esperas cortadas a los 30 s | 9,1 %, casi todos 503 "sistema ocupado" |
| Colapso total | desde ~330 usuarios | nunca |
| Recuperación con 5 usuarios | no se recuperó | mediana 110 ms y 0 errores a los 10 s |

### 6.10 Una sola alerta de faltante por insumo (RES-60)

Después de las pruebas de carga, la pantalla de Inventario del servidor tardaba en cargar y el Dashboard mostraba
**33 666 alertas de inventario** con solo 16 insumos.

**Causa:** cuando una venta necesita más de lo que hay registrado (RF-06), `ControlAlertas.faltante` crea una
alerta ALTA que pide un conteo físico. La creaba **en cada venta**, aunque el insumo ya tuviera una igual pendiente.
Las pruebas vendieron miles de pedidos con los insumos en 0, y se acumularon unas 2 000 alertas por insumo. Esto
tenía dos efectos:

- La pantalla de Inventario pide todas las alertas pendientes: decenas de miles de filas.
- Cada cobro revisa las alertas pendientes de cada insumo de la receta. Por eso el P95 de cobrar subió de 229 a
  389 ms entre la primera y la segunda prueba de carga.

En un restaurante real pasaría lo mismo, solo que más despacio: con un insumo sin registrar, cada venta del día
crearía otra alerta igual.

**Corrección:** si el insumo ya tiene una alerta de faltante pendiente, no se crea otra. Basta con un aviso hasta que
alguien lo atienda o se reabastezca el insumo, y en ese momento se cierra sola, como las demás. **No se pierde
información:** cada faltante sigue quedando en el movimiento de la venta (*"Venta pedido #N · faltaron 2"*) y en el
registro de auditoría. Para reconocer estas alertas se agregó `Alerta.esDeFaltante()`, igual que la que ya existía
para RestoFlow AI (`esDeRestoFlowAI()`).

**Pruebas nuevas** (`AlertasServiceTest`, +3):
- Tres ventas con faltante del mismo insumo dejan una sola alerta pendiente: la primera.
- Si se atiende la alerta, el siguiente faltante vuelve a avisar.
- El faltante de un insumo no impide avisar el de otro.

**Datos de pre-producción:** las pruebas de carga dejaron unas 5 500 ventas, 20 000 movimientos y 33 000 alertas de
prueba en la base del servidor. Con esos datos, *"Alcanza para"* y RestoFlow AI calculaban con un consumo inflado
durante 7 días (por ejemplo, *"comprar 603 aguacates"*). Por eso se reinició **solo el volumen de SQL Server**
(`docker volume rm restoflow_sqlserver_datos`). Al arrancar, `db-init` volvió a crear las tablas y el perfil
`preprod` volvió a cargar el menú de ejemplo y el inventario inicial. Los registros del backend se conservaron, y
los resultados de las pruebas están en `docs/pruebas/carga-y-estres.md`.

### 6.11 Paso a producción y versión 1.0.0 (RES-58)

El servidor de AWS pasa de la rama `preprod` a `main`, con el perfil `prod` y las imágenes en la versión **1.0.0**.
`main` queda con la etiqueta `v1.0.0`, que es la versión de la entrega del 15 de noviembre.

**Decisión: producción con los datos actuales.** Al pasar a `prod` no se borró la base de datos. El menú y el
inventario de ejemplo que cargó `preprod` después del reinicio de RES-60, y las cuentas del personal, se conservan
para la demostración. Cambiar de perfil no borra datos: `prod` solo deja de crear el menú de ejemplo cuando la base
está vacía.

**Swagger en producción, apagado por defecto.** El perfil `prod` apagaba Swagger sin excepción. Para mostrar el API
en la sustentación, se volvió configurable:

| Archivo | Cambio |
|---|---|
| `application-prod.yml` | `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled` leen `SWAGGER_HABILITADO` (por defecto `false`) |
| `docker-compose.yml` | Pasa `SWAGGER_HABILITADO` al backend (por defecto `false`) |
| `.env.example` | Documenta la variable y cuándo usar cada valor |
| `frontend/nginx.conf` | Reenvía `/swagger-ui` y `/v3/api-docs` al backend. Antes solo reenviaba `/api`, así que Swagger no se podía abrir desde el servidor |

Así un restaurante real lo deja apagado sin tocar código, y en la demostración se enciende con una variable. Aunque
la documentación esté visible, cada operación sigue exigiendo el token JWT: Swagger muestra el API, no lo abre.

**Versión 1.0.0:** `pom.xml` (también la ven `/actuator/info` y el `User-Agent` de Open Food Facts), las imágenes de
Docker (`RESTOFLOW_VERSION`), `.env.example` y `docs/doce-factores.md`.

**Correo en producción:** `prod` usa Gmail para la recuperación de contraseña, así que el `.env` del servidor
necesita `MAIL_USERNAME` y `MAIL_PASSWORD`. Se copian a mano, como la clave de Gemini, y nunca se suben al
repositorio. Los pasos están en `docs/despliegue-aws.md` §8.

### 6.12 Validación de requerimientos y versión 1.1.0 (RES-46)

Se revisaron uno por uno los 12 requerimientos funcionales y los 7 no funcionales del documento *Identificación y
Clasificación de los Requerimientos*. El resultado, con la evidencia de cada uno, está en
[`docs/validacion-requisitos.md`](validacion-requisitos.md).

La primera revisión encontró **dos requerimientos que se cumplían solo en parte**, y se completaron:

| Requerimiento | Faltaba | Cambio |
|---|---|---|
| RF-09 Estadísticas | Un reporte de tiempos de preparación. El sistema los calculaba para los avisos, pero no los mostraba | `TiemposPreparacion.porPlato` y una tabla en Reportes con los pedidos medidos, el tiempo normal (mediana) y el promedio de cada plato |
| Usabilidad | Que la comanda mostrara el stock | `ConsultarStockCocinaService` y `GET /api/cocina/stock`. Bajo cada plato se avisan los insumos en su mínimo o agotados |

**Decisiones:**
- **Solo se avisa lo que necesita atención:** la comanda no muestra el stock de los insumos que alcanzan, para no
  llenarla de datos que la cocina no necesita.
- **Pocas peticiones:** la pantalla de Cocina se actualiza cada pocos segundos, pero el stock solo se vuelve a
  pedir si cambian los platos o cada 30 segundos. La prueba de carga mostró que la pantalla de cocina ya es una de
  las más consultadas.
- **Un caso de uso nuevo:** el stock se resolvió con `ConsultarStockCocinaUseCase` y no agregando dependencias a
  `GestionarCocinaService`. Así la cocina no queda acoplada al inventario, y las pruebas existentes no cambian.
- **Versión 1.1.0:** estos cambios llegan después de la etiqueta `v1.0.0`. Por versionado semántico la nueva versión
  es **1.1.0**, porque agrega funciones sin cambiar las que ya existían.

**Pruebas:** 4 nuevas en el backend (`ConsultarStockCocinaServiceTest` y la estadística de tiempos en
`AvisosDominioTest`) y 5 en el frontend (Cocina, Reportes y la URL del servicio).

### 6.13 Problemas nuevos de SonarQube corregidos y versión 1.1.1 (RES-61)

El análisis de SonarQube de la versión 1.1.0 **no pasó la puerta de calidad** del backend. La regla de la puerta
pide **0 problemas nuevos**, y desde la versión 0.3.0 se habían acumulado 50: 14 de confiabilidad y 36 de
mantenibilidad. Ninguno era una vulnerabilidad, pero varios sí podían causar errores. Se corrigieron así:

| Problema (regla) | Dónde | Corrección |
|---|---|---|
| Comparar `DayOfWeek` con `==` (S8696) | `AnalizadorVentas`, `HistorialDemoConfig`, una prueba | `.equals()` |
| Restar fechas locales sin zona horaria (S8700) | `TiemposPreparacion`, `GeneradorAvisos` | Clase `Duraciones`: ubica las fechas en la zona del servidor (`TZ`) antes de restarlas |
| Posible `NullPointerException` (S2259) | `TiemposPreparacion.minutosNormales`, `GeminiAdapter`, `HistorialDemoConfig` | Un pedido sin platos usa el tiempo por defecto; sin modelos de Gemini se responde "no disponible"; mesas ocupadas vacías si la consulta no devuelve nada |
| `Optional` comparado con `null` (S2789) | `HistorialDemoConfig` | Se lee el usuario con `orElse(null)` |
| `volatile` en un objeto (S3077) | `AsistenteIAService` | `AtomicReference` |
| Mezcla de `Integer` y `Long` en un ternario (S2154) | `DescontarInventarioService` | Se convierte a texto cada rama |
| Método transaccional llamado con `this` (S6809) | `GestionarPedidosService` | Método privado `buscar` para el uso interno |
| Complejidad cognitiva 27 (S3776), ternario anidado, regex en un ciclo | `InstruccionesIA` | El contexto para la IA se arma en 4 métodos; la viñeta es un `Pattern` constante |
| Registro (log) que calcula datos aunque el nivel esté apagado (S2629) | `Auditoria` | `if (log.isInfoEnabled())` |
| `Thread.sleep` en pruebas (S2925) | 2 pruebas | Awaitility en la prueba de integración; espera con tiempo límite en la de la caché |
| Lambdas de `assertThrows` con varias llamadas (S5778) | 15 pruebas | Los datos se preparan antes del `assertThrows` |
| Literales repetidos, import sin usar, `equals` faltante en un `Clock` | Varios | Constante `MESA`, import borrado, `equals`/`hashCode` en `RelojAjustable` |
| Comentarios con la palabra "todo" (S1135) | 3 comentarios | SonarQube los tomaba como tareas pendientes (*TODO*); se reescribieron |

**Dos casos se dejaron con una justificación (`// NOSONAR`):**
- `RegistroPeticionJpaRepository` recibe 8 y 9 parámetros (S107). Spring Data necesita un parámetro por cada
  filtro de la consulta, y agruparlos obligaría a usar expresiones SpEL dentro del JPQL.
- `MesaOcupadaException` tiene 6 niveles de herencia (S110). SonarQube cuenta también `Throwable`, `Exception` y
  `RuntimeException`. Heredar de `OperacionNoPermitidaException` es lo que hace que el API responda 409.

**Versión 1.1.1:** son correcciones que no cambian ninguna función. Por versionado semántico se sube el último número.

**Pruebas:** las 316 del backend siguen pasando.
