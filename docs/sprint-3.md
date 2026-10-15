# Sprint 3 · Inventario, panel administrativo e historial

Responsable: **Brayan Estiven Sarmiento Vargas** (backend, frontend y documentación).

**Objetivo del sprint:** controlar el inventario del restaurante y conectarlo con las ventas: cada plato vendido
descuenta sus insumos automáticamente, el sistema avisa cuando algo se está acabando, y el administrador ve en un
panel cómo va el día y el historial de pedidos, ventas y movimientos.

En Jira es el **Sprint 4** (17 al 26 de octubre): épicas *Gestión de Inventario* (RES-4) y *Panel de Administración
y Reportes* (RES-5), tareas RES-29 a RES-35.

> Este documento cubre la **parte 1: backend**. Las pantallas (Inventario, Dashboard e Historial) se agregan en la
> parte 2.

## Decisiones del sprint

| Pregunta | Decisión | Por qué |
|---|---|---|
| ¿Cuándo se descuenta el inventario? | **Al registrar la venta** (pedido Pagado) | Un pedido cancelado nunca toca el inventario, y coincide con el modelo de datos: una venta es un pedido Pagado |
| ¿Y si no alcanza el stock? | **La venta se registra igual**, el insumo queda en 0 y se crea una **alerta ALTA** | El plato ya se sirvió; bloquear el cobro no tiene sentido. La alerta avisa que el inventario registrado no coincide con el real |
| ¿Cómo se registra un ajuste? | El movimiento guarda el **valor contado**, y el motivo dice cuánto había registrado | Así el historial muestra el antes y el después de cada conteo físico |
| ¿Se pueden borrar insumos? | No: se **desactivan** | Para no perder su historial de movimientos (igual que los productos) |

## Tareas

| Jira | Tarea | Qué se hizo |
|---|---|---|
| RES-29 | RF-05 Consultar y actualizar existencias | Insumos con unidad, stock actual y mínimo; crear, editar, activar y desactivar |
| RES-32 | Registrar entradas y salidas | Entradas (compras), salidas (mermas, con motivo obligatorio) y ajustes por conteo físico |
| RES-30 | RF-06 Descuento automático según pedidos | Recetas por producto y descuento al registrar la venta |
| RES-35 | Sincronizar pedidos e inventario (integridad) | Venta, descuento, movimientos y alertas en **una sola transacción** |
| RES-31 | RF-07 Notificar niveles bajos | Alertas automáticas al llegar al mínimo o agotarse; se cierran solas al reabastecer |
| RES-33 | RF-08 Panel administrativo | Resumen del día: ventas, ticket promedio, comparación con ayer, ventas por hora, más vendidos, insumos bajos |
| RES-34 | RF-12 Historial | Pedidos y ventas por rango de fechas; movimientos de inventario con filtros |

## Reglas de negocio implementadas

**Inventario (entidad `Ingrediente`)**
- El stock solo cambia con movimientos: entrada, salida o ajuste. Editar un insumo cambia nombre, unidad y mínimo,
  nunca el stock.
- Una salida manual no puede dejar el stock en negativo (409). El consumo por venta tampoco: si no alcanza, queda
  en 0 e informa cuánto faltó.
- Un insumo está "en su mínimo" cuando `stock_actual ≤ stock_minimo`.
- Cada movimiento guarda quién lo hizo, cuándo, cuánto y por qué. Los que genera una venta los firma el
  **Sistema** y llevan el número del pedido.

**Descuento por venta (`DescontarInventarioService`)**
1. Suma el consumo de cada insumo en todo el pedido: 2 bandejas × 150 g de arroz + 1 ajiaco × 80 g = **380 g**.
2. Descuenta y guarda **una salida por insumo**, no una por línea del pedido.
3. Revisa las alertas de cada insumo que cambió.
4. Los productos sin receta (por ejemplo, una gaseosa sin insumo asociado) no descuentan nada.

**Alertas (`ControlAlertas`)**
- Llega al mínimo → alerta **MEDIA**: "Quedan 900 gramo de Arroz (mínimo 1000)".
- Se agota → alerta **ALTA**, que reemplaza a la MEDIA pendiente.
- Una venta necesitó más de lo registrado → alerta **ALTA** que pide hacer un conteo físico.
- Vuelve a estar por encima del mínimo → sus alertas pendientes se marcan como atendidas solas.
- Nunca hay dos alertas pendientes iguales para el mismo insumo.

**Integridad (RES-35).** `GestionarPedidosService.registrarVenta` llama al descuento dentro de su misma
transacción (`@Transactional`). Si cualquier paso falla, Spring deshace todo: ni la venta ni el descuento quedan a
medias.

## Arquitectura (lo nuevo)

Sigue el mismo camino hexagonal de los sprints anteriores:

| Capa | Archivos nuevos |
|---|---|
| Dominio | `Ingrediente` (consumir, actualizar, agotado), `MovimientoInventario.registrar`, nombres para mostrar en receta, movimiento y alerta |
| Puertos de entrada | `GestionarInventarioUseCase`, `GestionarRecetasUseCase`, `DescontarInventarioUseCase`, `GestionarAlertasUseCase`, `ConsultarPanelUseCase`, `ConsultarHistorialUseCase` |
| Servicios | `GestionarInventarioService`, `GestionarRecetasService`, `DescontarInventarioService`, `ControlAlertas`, `GestionarAlertasService`, `ConsultarPanelService`, `ConsultarHistorialService` |
| Puertos de salida | `IngredienteRepositoryPort`, `RecetaRepositoryPort`, `MovimientoRepositoryPort`, `AlertaRepositoryPort` y dos consultas nuevas en `PedidoRepositoryPort` |
| Persistencia | 4 repositorios JPA y 4 adaptadores; consultas con `@EntityGraph` para traer los datos relacionados en una sola consulta |
| Web | `InventarioController`, `RecetaController`, `AlertaController`, `PanelController`, `HistorialController` e `InventarioDto` |
| Datos de ejemplo | `InventarioInicialConfig`: 16 insumos y recetas para los 9 productos del menú de ejemplo (solo en desarrollo y pre-producción) |

No se cambió el script SQL: las tablas `ingredientes`, `receta_ingredientes`, `movimientos_inventario` y
`alertas` ya existían desde el Sprint 2.

## Contrato del API

| Método | Ruta | Acceso | Cuerpo |
|---|---|---|---|
| GET | `/api/inventario/ingredientes?soloActivos=` | INVENTARIO, ADMIN | — |
| GET | `/api/inventario/ingredientes/{id}` | INVENTARIO, ADMIN | — |
| POST | `/api/inventario/ingredientes` | INVENTARIO, ADMIN | `{ nombre, unidad, stockMinimo, stockInicial }` |
| PUT | `/api/inventario/ingredientes/{id}` | INVENTARIO, ADMIN | `{ nombre, unidad, stockMinimo }` |
| PATCH | `/api/inventario/ingredientes/{id}/estado` | INVENTARIO, ADMIN | `{ activo }` |
| POST | `/api/inventario/ingredientes/{id}/entradas` | INVENTARIO, ADMIN | `{ cantidad, motivo }` |
| POST | `/api/inventario/ingredientes/{id}/salidas` | INVENTARIO, ADMIN | `{ cantidad, motivo }` (motivo obligatorio) |
| POST | `/api/inventario/ingredientes/{id}/ajustes` | INVENTARIO, ADMIN | `{ stockContado, motivo }` |
| GET | `/api/inventario/movimientos?ingredienteId=&tipo=&desde=&hasta=` | INVENTARIO, ADMIN | — |
| GET | `/api/productos/{id}/receta` | Con sesión | — |
| PUT | `/api/productos/{id}/receta` | ADMIN | `{ lineas: [{ ingredienteId, cantidad }] }` |
| GET | `/api/alertas?soloPendientes=` | INVENTARIO, ADMIN | — |
| PATCH | `/api/alertas/{id}/atender` | INVENTARIO, ADMIN | — |
| GET | `/api/panel/resumen?fecha=` | ADMIN | — (sin fecha: hoy) |
| GET | `/api/historial/pedidos?desde=&hasta=&estado=` | ADMIN | — |
| GET | `/api/historial/ventas?desde=&hasta=` | ADMIN | — |

Unidades de medida: `GRAMO`, `KILOGRAMO`, `MILILITRO`, `LITRO`, `UNIDAD`. Fechas en formato `AAAA-MM-DD`; el
historial admite máximo 92 días por consulta. `PATCH /api/pedidos/{id}/pagar` ahora también descuenta el
inventario.

## Resultado

- **68 pruebas automáticas en verde** (53 de antes + 15 nuevas):
  - `IngredienteTest`: consumo por venta y edición.
  - `GestionarInventarioServiceTest`: entradas, salidas, ajustes, ciclo completo de alertas e historial.
  - `DescontarInventarioServiceTest`: descuento por receta, venta sin stock suficiente, productos sin receta y
    reglas de la receta.
  - `ConsultarPanelServiceTest`: resumen del día e historial por rango.
  - `InventarioFlujoIntegracionTest`: todo por el API, con tokens reales de administrador, inventario y mesero.
    Recorre crear insumo → receta → vender → ver el descuento y la alerta → reabastecer → panel e historial, y
    comprueba quién puede hacer qué.
- Se comprobó que los datos de ejemplo se cargan: 16 insumos y una receta para cada producto del menú.

## Cómo probarlo (Swagger)

1. Arrancar el backend en desarrollo. Si la base no tiene insumos, se cargan los de ejemplo con sus recetas.
2. Abrir http://localhost:8080/swagger-ui.html, iniciar sesión como administrador y usar **Authorize** con el token.
3. **Inventario → GET /api/inventario/ingredientes**: ver los insumos y su stock.
4. Con el mesero y la cocina, vender 2 bandejas paisa: crear el pedido, avanzarlo hasta Entregado y pagarlo.
5. Volver a consultar los insumos: el arroz bajó 300 g y los huevos 2 unidades. En
   **GET /api/inventario/movimientos?tipo=SALIDA** aparece la salida firmada por el Sistema con el número del pedido.
6. **GET /api/panel/resumen**: ventas del día, la bandeja entre los más vendidos y los insumos en su mínimo.
