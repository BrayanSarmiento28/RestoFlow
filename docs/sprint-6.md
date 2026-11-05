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

