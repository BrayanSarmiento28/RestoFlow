# Validación de requerimientos (RES-46)

Esta validación revisa, uno por uno, los requerimientos del documento *Identificación y Clasificación de los
Requerimientos* (Sprint 1) frente a lo construido. Se hizo sobre la **versión 1.0.0**, desplegada en AWS con el
perfil `prod`. Los dos requerimientos que se cumplían solo en parte se completaron en la **versión 1.1.0**: una
versión *menor*, porque agrega funciones sin cambiar las que ya existían.

Para cada requerimiento se indica:

- **Cómo se cumple:** las pantallas y la parte del API que lo resuelven.
- **Evidencia:** las pruebas automáticas que lo verifican, o la medición que lo demuestra.
- **Estado:** **Cumple**, **Cumple parcialmente** o **No cumple**. Cuando algo no está completo se dice qué falta.

**Pruebas automáticas:** 316 en el backend (JUnit 5, con integración sobre H2 y MockMvc) y
unas 250 en el frontend (Jest), en 32 archivos. Las del backend se corren con `.\mvnw.cmd test` dentro de
`backend/`, y las del frontend con `npm test` dentro de `frontend/`.

## Resumen

| Código | Requerimiento | Estado |
|---|---|---|
| RF-01 | Autenticación de usuarios | Cumple |
| RF-02 | Gestión de pedidos | Cumple |
| RF-03 | Gestión de estados | Cumple |
| RF-04 | Visualización de pedidos | Cumple |
| RF-05 | Control de inventario | Cumple |
| RF-06 | Actualización automática del inventario | Cumple |
| RF-07 | Alertas de inventario | Cumple |
| RF-08 | Consulta de información administrativa | Cumple |
| RF-09 | Generación de estadísticas | Cumple |
| RF-10 | Análisis de datos | Cumple |
| RF-11 | Recomendaciones | Cumple |
| RF-12 | Historial de operaciones | Cumple |
| RNF | Seguridad | Cumple |
| RNF | Rendimiento | Cumple |
| RNF | Usabilidad | Cumple |
| RNF | Disponibilidad | Cumple |
| RNF | Escalabilidad | Cumple |
| RNF | Alertas | Cumple |
| RNF | Confiabilidad | Cumple |

**Se cumplen los 19 requerimientos.** En la primera revisión, dos se cumplían solo en parte, y se completaron
dentro de RES-46:
- **RF-09:** faltaba una estadística de tiempos de preparación.
- **Usabilidad:** la comanda no mostraba el stock.

El detalle está en cada sección.

## Requerimientos funcionales

### RF-01 · Autenticación de usuarios — Cumple

> El sistema debe permitir a los usuarios iniciar sesión mediante sus credenciales y acceder según su rol.

**Cómo se cumple:**
- **Pantallas:** Iniciar sesión, Recuperar contraseña, Mi cuenta (cambiar contraseña) y Usuarios.
- **API:** `POST /api/auth/login`, `/recuperar`, `/restablecer`, `PATCH /contrasena` y `/api/usuarios`.
- **Roles:** existen cuatro: Administrador, Mesero / Cajero, Personal de cocina y Personal de inventario.
- **Acceso por rol en el backend:** cada operación del API exige un rol (`SecurityConfig`). Por ejemplo, solo el
  administrador cobra.
- **Acceso por rol en el frontend:** los guardas `adminGuard` y `rolGuard` ocultan y bloquean las pantallas que no
  le corresponden a cada usuario.
- **Sesión:** se maneja con un token JWT que dura 8 horas.
- **Cuentas:** el administrador crea las cuentas y puede desactivarlas o reactivarlas. Una cuenta desactivada no
  puede iniciar sesión, y si ya tenía sesión abierta, su token deja de servir.
- **Recuperación de contraseña:** se envía un código de un solo uso por correo real (Gmail). Se verificó en
  producción el 11 de noviembre.

**Evidencia:** `IniciarSesionServiceTest`, `RecuperarContrasenaServiceTest`, `CodigoRecuperacionTest`,
`CambiarContrasenaServiceTest`, `GestionarCuentasServiceTest`, `CuentaIntegracionTest`, `UsuarioTest` y
`SeguridadAdaptadoresTest`. En el frontend, `sesion.test.ts` y las pruebas de los guardas.

### RF-02 · Gestión de pedidos — Cumple

> El sistema debe permitir al mesero o cajero registrar, modificar y consultar los pedidos realizados por los
> clientes.

**Cómo se cumple:**
- **Pantalla:** Pedidos, para el mesero / cajero y el administrador.
- **API:** `/api/pedidos`.

| Acción | Cómo funciona |
|---|---|
| Registrar | Se elige la mesa, los productos y una nota por producto |
| Modificar | Solo mientras el pedido está *Pendiente*. Después se pueden agregar **adiciones** y cambiar de mesa |
| Consultar | Con filtros por mesa y por estado |
| Cancelar | Solo en *Pendiente* |
| Cobrar | Solo cuando el pedido está *Entregado*. Lo hace el administrador |

- Una mesa ocupada no recibe un segundo pedido abierto.

**Evidencia:** `PedidoTest` (19 pruebas de reglas del pedido), `GestionarPedidosServiceTest` (14),
`PedidoFlujoIntegracionTest` y `ReglasDeNegocioIntegracionTest`.

### RF-03 · Gestión de estados — Cumple

> El sistema debe permitir al personal de cocina actualizar el estado de los pedidos entre pendientes, en
> preparación, listos y entregados.

**Cómo se cumple:**
- **Pantalla:** Cocina, con un botón que avanza al siguiente estado.
- **API:** `PATCH /api/cocina/pedidos/{id}/estado`.
- Los estados van en orden: *Pendiente → En preparación → Listo → Entregado*. No se puede saltar ni retroceder un
  estado: el API responde 409. Cada cambio guarda su hora, y esas horas son las que usa RestoFlow AI para aprender
  los tiempos de preparación.

**Evidencia:** `PedidoTest` (orden de los estados), `ReglasDeNegocioIntegracionTest` y
`PedidoFlujoIntegracionTest`.

### RF-04 · Visualización de pedidos — Cumple

> El sistema debe mostrar al personal de cocina los pedidos registrados y los productos que debe preparar.

**Cómo se cumple:**
- **Pantalla:** Cocina, que se actualiza sola cada pocos segundos. Las comandas se ordenan por hora de llegada, en
  columnas por estado.
- **API:** `GET /api/cocina/comandas`.

Cada comanda muestra:
- la mesa y el tiempo de espera, con una barra que se pone roja a los 20 minutos;
- la hora y el mesero;
- los productos con su cantidad y la nota de cada uno;
- las observaciones y las adiciones resaltadas.

**Evidencia:** `PedidoFlujoIntegracionTest` y las pruebas de la pantalla de Cocina (`cocina.test.ts`).

### RF-05 · Control de inventario — Cumple

> El sistema debe permitir al personal autorizado registrar, consultar y actualizar las existencias de productos
> e ingredientes.

**Cómo se cumple:**
- **Pantalla:** Inventario, para el personal de inventario y el administrador.
- **API:** `/api/inventario/ingredientes`.

Qué se puede hacer:
- **Registrar insumos:** con unidad y stock mínimo. También se pueden autocompletar los datos con el código de
  barras, usando el catálogo externo Open Food Facts.
- **Consultar:** el stock, el estado y para cuántos días alcanza.
- **Actualizar existencias:** con entradas, salidas y ajustes, cada uno con su motivo.

Las existencias de los **productos** del menú dependen de sus ingredientes, según la receta. Además, el
administrador puede marcar un producto como no disponible.

**Evidencia:** `GestionarInventarioServiceTest`, `InventarioDominioTest`, `IngredienteTest`,
`InventarioFlujoIntegracionTest` y `OpenFoodFactsAdapterTest`.

### RF-06 · Actualización automática del inventario — Cumple

> El sistema debe descontar automáticamente del inventario los ingredientes asociados a los productos vendidos.

**Cómo se cumple:**
- Cada producto tiene su **receta**: los insumos y la cantidad de cada uno. Se define en Productos, con
  `PUT /api/productos/{id}/receta`.
- Al **cobrar** un pedido, en la misma transacción se descuenta cada insumo según la receta y la cantidad vendida,
  y se registra un movimiento de salida con el número del pedido.
- Si no alcanza, la venta igual se registra, el insumo queda en 0 y se crea una alerta de faltante para hacer un
  conteo físico.

**Concurrencia (RES-45):** cuando varios cobros simultáneos usan el mismo insumo, cada uno lo reserva antes de
descontar. Así no se pierde ningún descuento.

**Evidencia:**
- `DescontarInventarioServiceTest`.
- `ConcurrenciaInventarioIntegracionTest`: 10 cobros simultáneos y el stock baja exactamente 10.
- `InventarioFlujoIntegracionTest`.
- Prueba de carga en AWS: 1 381 ventas sin errores, cada una con su descuento.

### RF-07 · Alertas de inventario — Cumple

> El sistema debe generar una alerta cuando la existencia de un producto o ingrediente alcance un nivel mínimo
> establecido.

**Cómo se cumple:**
- **Alerta automática:** al llegar al mínimo se crea una alerta **media**, y al agotarse, una **alta**. No se
  repiten.
- **Cierre automático:** las alertas se cierran solas al reabastecer, y el personal también puede marcarlas como
  atendidas.
- **Dónde se ven:** en Inventario, en el Dashboard y en la campana de avisos.
- **API:** `/api/alertas`.
- **Alerta de faltante:** desde RES-60 se crea una sola alerta de faltante por insumo. Antes se repetía en cada
  venta.

**Evidencia:** `AlertasServiceTest` (11 pruebas) y `DescontarInventarioServiceTest`. Para mostrarlo en vivo, en
Inventario se registra una salida que deje el insumo en su mínimo, y la alerta aparece en ese momento.

### RF-08 · Consulta de información administrativa — Cumple

> El sistema debe permitir al administrador consultar información sobre ventas, pedidos, inventario y
> funcionamiento del restaurante.

**Cómo se cumple:** el Dashboard (`GET /api/panel/resumen`) reúne la información del día.

| Tema | Qué muestra |
|---|---|
| Ventas | Ventas del día, comparación con el día anterior, ticket promedio, ventas por hora, por mesero y por método de pago (para cuadrar la caja) |
| Pedidos | Pedidos abiertos, en cocina y cancelados, y los productos más vendidos |
| Inventario | Insumos en su mínimo y alertas pendientes |
| Funcionamiento | El **registro de actividad** (`/api/registro`) guarda cada petición con el usuario y el tiempo de respuesta, y los avisos del momento muestran los pedidos demorados o sin cobrar |

**Evidencia:** `ConsultarPanelServiceTest`, `ConsultarPanelEnCacheServiceTest`, `RegistroActividadServiceTest`,
`RegistroActividadIntegracionTest` y `AvisosIntegracionTest`.

### RF-09 · Generación de estadísticas — Cumple

> El sistema debe generar estadísticas sobre ventas, productos más solicitados, cantidad de pedidos y tiempos de
> preparación.

| Estadística | Dónde | Estado |
|---|---|---|
| Ventas | Reportes: total del periodo, ticket promedio y ventas por día. Dashboard: ventas por hora | Cumple |
| Productos más solicitados | Dashboard (más vendidos del día) y RestoFlow AI | Cumple |
| Cantidad de pedidos | Reportes (pedidos cobrados del periodo) y Dashboard (registrados, abiertos y cancelados) | Cumple |
| Tiempos de preparación | Reportes → **Tiempos de preparación**: por cada plato vendido en el periodo, cuántos pedidos se midieron, el **tiempo normal** (mediana) y el **promedio**, del más lento al más rápido. Se mide desde que se registra el pedido hasta que la cocina lo marca Listo. Los mismos tiempos alimentan los avisos de pedido demorado (*"lo normal para Limonada de coco son 5 min"*) | Cumple |

**Por qué la mediana y el promedio:** si un día un pedido se quedó olvidado 2 horas, el promedio se dispara pero la
mediana casi no cambia. Mostrar los dos deja ver cuándo hubo pedidos atípicos.

**Evidencia:** `VentasTest`, `ConsultarHistorialServiceTest`, `ConsultarPanelServiceTest`, `AvisosDominioTest`
(*"Estadística por plato: pedidos medidos, tiempo normal (mediana) y promedio"*) y `reportes.test.ts`.
`GET /api/historial/ventas` incluye `tiemposPreparacion`.

### RF-10 · Análisis de datos — Cumple

> El sistema debe analizar los datos históricos de pedidos y ventas para identificar tendencias y comportamientos
> relevantes.

**Cómo se cumple:** RestoFlow AI (`GET /api/restoflow-ai/analisis`) analiza las ventas de las últimas semanas.
Calcula:
- la demanda por día de la semana y la predicción de mañana, con la variación frente a un día normal;
- los productos que suben o bajan;
- el consumo diario de cada insumo y para cuántos días alcanza.

Con Google Gemini, además, redacta el análisis en lenguaje natural y responde preguntas del administrador
(`POST /api/restoflow-ai/preguntar`). Si Gemini no está disponible, el análisis sigue funcionando con reglas
propias.

**Evidencia:** `RestoFlowAIDominioTest` (12), `RestoFlowAIServiceTest`, `RestoFlowAIIntegracionTest`,
`GeminiAdapterTest`, `AsistenteIAServiceTest` e `InstruccionesIATest`.

### RF-11 · Recomendaciones — Cumple

> El sistema debe generar recomendaciones al administrador relacionadas con inventario y demanda a partir de los
> datos analizados.

**Cómo se cumple:**
- **Inventario:** qué comprar y cuánto, con prioridad. Sale del consumo real y del stock mínimo, por ejemplo
  *"Comprar 12 und de Aguacate · Alta"*. Además, cada hora se crean alertas anticipadas, como *"se acaba en 2
  días"*.
- **Demanda:** avisa si mañana será un día fuerte y cuáles serán los productos más pedidos.
- **Dónde se ven:** en RestoFlow AI, en el Dashboard y en la campana de avisos, que muestra a cada rol lo suyo.
- **Redacción:** Gemini redacta la recomendación principal. En producción se verificó el 11 de noviembre que
  aparece *"Redactado por Gemini"*.

**Evidencia:** `AvisosDominioTest`, `AvisosServiceTest`, `RestoFlowAIDominioTest` y `AsistenteIAIntegracionTest`.

### RF-12 · Historial de operaciones — Cumple

> El sistema debe almacenar y permitir consultar el historial de pedidos, ventas y movimientos de inventario.

**Cómo se cumple:**
- **Pantalla:** Reportes, con filtro de fechas, que muestra el historial de **pedidos**, de **ventas** y de
  **movimientos de inventario**.
- **API:** `GET /api/historial/pedidos`, `/api/historial/ventas` y `/api/inventario/movimientos`.
- **Inventario:** también muestra el historial de los últimos 7 días.
- **Qué se guarda:** todo queda en SQL Server. Cada movimiento lleva su tipo, la cantidad, el motivo, el usuario y
  el pedido que lo causó. Además, la **auditoría** (`auditoria.log`) registra las acciones importantes.

**Evidencia:** `ConsultarHistorialServiceTest`, `InventarioFlujoIntegracionTest` y `LogsIntegracionTest`.

## Requerimientos no funcionales

### Seguridad — Cumple

> Proteger todas las contraseñas para que no puedan ser consultadas y vistas directamente. *Validación: comprobar
> que las contraseñas almacenadas en la base de datos no estén en texto visible.*

**Cómo se cumple:**
- **Contraseñas:** se guardan cifradas con **BCrypt** (`BCryptCifradoAdapter`), un cifrado de un solo sentido y con
  sal. Nadie puede leerlas, ni siquiera el administrador de la base de datos.
- **Códigos de recuperación:** también se guardan cifrados y vencen.
- **Otras medidas:**
  - token JWT firmado;
  - permisos por rol en cada operación;
  - los secretos (claves, contraseñas de la base de datos, de Gmail y de Gemini) solo existen en variables de
    entorno del servidor y nunca en el repositorio;
  - el backend y SQL Server no se exponen a internet: solo responde Nginx en el puerto 80.

**Evidencia:**
- `SeguridadAdaptadoresTest` comprueba que el hash no es la contraseña y que verifica bien.
- `UsuarioTest`.
- **En el servidor:** la consulta de abajo muestra que la columna solo tiene el hash, que empieza por `$2a$`, la
  marca de BCrypt.

```bash
docker compose exec db bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d RestoFlowDB -Q "SELECT email, LEFT(password_hash, 20) AS inicio_del_hash FROM usuarios"'
```

### Rendimiento — Cumple

> Responder a las operaciones principales en máximo 3 o 4 segundos. *Validación: realizar consultas y verificar
> que el tiempo de respuesta no supere los 3 segundos.*

**Cómo se cumple:** se midió de tres formas.

| Medición | Resultado |
|---|---|
| Un solo usuario (RES-28, `docs/pruebas/rendimiento.md`) | Todas las operaciones muy por debajo de 3 s |
| **Carga en AWS:** 100 usuarios a la vez durante 5 minutos, con JMeter desde Colombia hasta el servidor en Ohio (RES-56) | 16 319 peticiones, **0 errores**, **P95 de 284 ms**. La operación más lenta fue el Dashboard, con P95 de 548 ms. Todas cumplen 3 s |
| **Estrés en AWS:** hasta 600 usuarios | Cumple los 3 s hasta unos **240 usuarios a la vez**, 8 veces la hora pico de un restaurante mediano |

El criterio es el **percentil 95**: 95 de cada 100 peticiones responden en ese tiempo o menos. Es más exigente que
el promedio.

**Evidencia:** `docs/pruebas/carga-y-estres.md` y los resúmenes en `docs/pruebas/jmeter/resultados/`.

### Usabilidad — Cumple

> Presentar los pedidos de cocina de forma clara, mostrando como mínimo los productos, stock y estado. *Validación:
> crear pedidos de prueba y verificar que el personal de cocina identifique bien lo que va a preparar.*

**Lo que cumple:**
- Cocina muestra cada pedido como una comanda grande, con la mesa, los **productos con su cantidad**, la nota de
  cada uno, las observaciones y el tiempo de espera.
- El **estado** se ve por la columna en la que está (Pendiente, En preparación, Listos para entregar) y por el
  botón de la siguiente acción.
- La pantalla es oscura y de letra grande, pensada para leerse de lejos en la cocina, y se actualiza sola.
- Se validó en el servidor con pedidos de prueba.

- **Stock:** bajo cada plato de las comandas pendientes y en preparación aparecen los insumos de su receta que
  están **en su mínimo** (en ámbar, por ejemplo *"Aguacate: quedan 2 und"*) o **agotados** (en naranja, *"Mora
  agotado"*). Así la cocina sabe antes de empezar si alcanza para el plato.
- **Stock que alcanza:** no se muestra. La comanda solo avisa lo que necesita atención, para no llenarla de datos.
- **Cómo se consulta:** con `GET /api/cocina/stock`. La pantalla lo vuelve a pedir solo si cambian los platos o cada
  30 segundos, para no duplicar las peticiones. Si esa consulta falla, la cocina sigue trabajando sin el aviso.

**Evidencia:** pantalla de Cocina, `ConsultarStockCocinaServiceTest` (3 pruebas) y `cocina.test.ts`.

### Disponibilidad — Cumple

> Estar disponible durante los horarios de funcionamiento del restaurante, permitiendo consultar y actualizar los
> pedidos. *Validación: agregar progresivamente registros y realizar pruebas durante el horario.*

**Cómo se cumple:**
- **Servidor:** RestoFlow está en un servidor de AWS EC2 encendido todo el tiempo, con IP fija (`18.222.37.70`).
- **Reinicio automático:** cada contenedor tiene `restart: unless-stopped`, así que si un proceso se cae Docker lo
  levanta solo, y si el servidor se reinicia RestoFlow arranca sin que nadie intervenga.
- **Vigilancia:** los *healthchecks* de Docker revisan cada 30 segundos la base de datos, el backend y el
  frontend.
- **Saturación:** desde RES-59 el sistema se **recupera solo** después de saturarse. En el estrés, 10 segundos
  después del pico los tiempos volvieron a 110 ms.

**Evidencia:**
- Las pruebas de carga y estrés corrieron con el sistema en funcionamiento y agregando registros todo el tiempo:
  pedidos, cocina, cobros y descuentos de inventario.
- `ActuatorIntegracionTest`.
- `/api/salud`, que responde `"estado":"UP"`.

**Límite conocido:** es un solo servidor. Si AWS tuviera una falla en esa máquina, el sistema no estaría disponible
hasta reiniciarla. Para varias sedes o un restaurante grande se usarían varias copias del backend
(`docker-compose.escalado.yml`) detrás de un balanceador.

### Escalabilidad — Cumple

> Permitir agregar nuevos usuarios, productos y registros sin afectar el funcionamiento general. *Validación:
> agregar progresivamente registros y que el sistema funcione correctamente.*

**Cómo se cumple:**
- **Usuarios y productos:** se agregan desde la aplicación, sin tocar código.
- **Registros:** las pruebas de carga agregaron, en una tarde, unas 5 500 ventas, 20 000 movimientos de inventario
  y miles de pedidos, y el sistema siguió funcionando.
- **Hallazgos corregidos:**
  - RES-59: el Dashboard recorría todos los pedidos del día. Ahora se calcula una vez cada 5 s y se comparte.
  - RES-60: las alertas de faltante se repetían en cada venta.
- **Copias del backend:** el backend no guarda estado (JWT), así que se pueden encender varias copias con
  `docker-compose.escalado.yml`.

**Evidencia:** `docs/pruebas/carga-y-estres.md`, `docs/sprint-6.md` §6.9 y §6.10, y `ConsultarPanelEnCacheServiceTest`.

**Límite conocido:** la pantalla de Inventario carga los movimientos de los últimos 7 días. Con el volumen de un
restaurante real (cientos al día) carga al instante. Con los miles que crearon las pruebas de carga se volvía
lenta. Si el restaurante creciera mucho, convendría paginarla.

### Alertas — Cumple

> Generar alertas de inventario de manera automática cuando un producto o ingrediente alcance el mínimo.
> *Validación: reducir el inventario hasta el límite definido y comprobar que se genere la alerta.*

**Cómo se cumple:** es el mismo mecanismo de RF-07. Al registrar una salida o una venta que deja el insumo en su
mínimo, la alerta se crea en ese momento, sin que nadie la pida. El personal de inventario y el administrador la
ven en la campana, en Inventario y en el Dashboard. Además, RestoFlow AI crea alertas **anticipadas**, como *"se
acaba en 2 días"*.

**Evidencia:** `AlertasServiceTest` (*"Al llegar al mínimo se crea una alerta MEDIA"*, *"Al agotarse, la ALTA
reemplaza a la MEDIA"*). En vivo: registrar en Inventario una salida hasta el mínimo y ver la alerta.

### Confiabilidad — Cumple

> Conservar la información de pedidos, usuarios y productos sin que se modifique después de cerrar el sistema.
> *Validación: registrar información, cerrar el sistema, volver a iniciar y verificar que los datos estén
> almacenados correctamente.*

**Cómo se cumple:**
- **Persistencia:** los datos están en SQL Server, en un **volumen de Docker** (`sqlserver_datos`). Ese volumen
  sobrevive al apagado de los contenedores y del servidor, y a la reconstrucción de las imágenes.
- **Transacciones:** cada operación se guarda en una transacción completa. Un cobro, por ejemplo, guarda el pago,
  los descuentos y las alertas juntos, o nada.

**Evidencia, la validación tal como la pide el documento:**
- La noche del 10 de noviembre **se apagó el servidor de AWS**. Al encenderlo, el 11 de noviembre, seguían todos
  los datos del día anterior: cuentas, productos, pedidos y ventas.
- Cada actualización de versión (`docker compose up -d --build`) reemplazó el backend y el frontend sin perder
  datos. La base de datos siguió encendida 15 horas seguidas mientras se cambiaban los otros contenedores.
- `ConcurrenciaInventarioIntegracionTest`: no se pierden datos con operaciones simultáneas.

## Cambios hechos durante la validación

| Requerimiento | Antes | Cambio |
|---|---|---|
| RF-09 | Los tiempos de preparación se calculaban y se usaban en los avisos, pero no había un reporte | Tabla *Tiempos de preparación* en Reportes (`TiemposPreparacion.porPlato`, `ResumenVentas.tiemposPreparacion`) |
| Usabilidad | La comanda mostraba productos, cantidades y estado, pero no el stock | Aviso de stock bajo cada plato (`ConsultarStockCocinaService`, `GET /api/cocina/stock`) |
