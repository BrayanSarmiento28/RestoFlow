# Sprint 2 · Entidades, pedidos y cocina

Responsable: **Brayan Estiven Sarmiento Vargas** (backend, frontend y documentación).

**Objetivo del sprint:** atender las recomendaciones de la sustentación del Sprint 1 (entidades, entornos e
identidad visual) y poner a funcionar el flujo central del restaurante: el mesero toma el pedido, la cocina lo
prepara y el pedido se cobra.

En Jira estas historias están en el proyecto **RES**, épicas *Gestión de Pedidos* (RES-2) y *Módulo de Cocina*
(RES-3), tareas RES-22 a RES-28.

## Recomendaciones del docente atendidas

| Recomendación | Qué se hizo |
|---|---|
| Agregar entidades en backend y frontend | Backend: 10 entidades de dominio con sus reglas (`domain/model`) y sus 10 tablas JPA (`persistence/entity`), incluidas las de inventario y alertas para los siguientes sprints. Frontend: carpeta `core/entities` con una interfaz por entidad |
| Agregar environments | Cada archivo de entorno del frontend ahora define versión y tiempos de actualización; el backend define por entorno si carga el menú de ejemplo. Guía de GitHub Environments en `docs/entornos.md` |
| Identidad del proyecto (logo) | Logo propio de RestoFlow aplicado en login, menú lateral, pestaña del navegador y documentación |

## Historias de usuario

Los códigos corresponden al documento de historias de usuario del proyecto.

| Código | Historia | Resultado |
|---|---|---|
| HU_MF02_008 | Registrar pedido | El mesero indica la mesa; el pedido queda Pendiente, con su mesero, fecha y hora |
| HU_MF02_009 | Agregar productos al pedido | Solo productos activos; cada línea guarda el precio vigente; el total se ve en vivo |
| HU_MF02_010 | Modificar pedido | Cambiar cantidades, notas y productos solo mientras esté Pendiente (si no, 409) |
| HU_MF02_011 | Enviar pedido a cocina | Al enviarlo aparece en la pantalla de cocina; no se permite un pedido sin productos |
| HU_MF02_012 | Consultar pedidos y su estado | Lista con mesa, hora, total y estado; filtros por mesa y estado; detalle de productos |
| HU_MF02_013 | Cancelar pedido | Solo Pendiente; sale de la cocina y queda en el historial como Cancelado |
| HU_MF02_014 | Registrar la venta | Solo pedidos Entregados; quedan Pagados con fecha de cierre. El descuento de inventario llega con el sprint de inventario |
| HU_MF03_015 | Iniciar la preparación | Pendiente → En preparación, con la hora del cambio |
| HU_MF03_016 | Marcar pedido como listo | En preparación → Listo, con la hora |
| HU_MF03_017 | Marcar pedido como entregado | Listo → Entregado; sale de la cocina y habilita la venta |
| HU_MF04_018 | Ver los pedidos entrantes en cocina | Comandas del más antiguo al más reciente, en tarjetas de alto contraste que se actualizan solas |
| HU_MF04_019 | Ver el detalle de la comanda | Productos, cantidades, notas, mesa y mesero; solo lectura para cocina |

## Tareas

| # | Tarea | Estado |
|---|---|---|
| E1 | Entidades de dominio: Categoria, Producto, Pedido, DetallePedido, Ingrediente, RecetaIngrediente, MovimientoInventario, Alerta y sus enumerados | ✅ |
| E2 | Entidades JPA y script `02_menu_pedidos_inventario.sql` | ✅ |
| E3 | Entidades del frontend en `core/entities` | ✅ |
| P1 | Casos de uso de menú, pedidos y cocina (puertos de entrada y salida) | ✅ |
| P2 | Adaptadores de persistencia y controladores REST documentados en Swagger | ✅ |
| P3 | Reglas de acceso por rol en `SecurityConfig` | ✅ |
| P4 | Pantallas de Pedidos, Cocina y Productos; menú lateral según el rol | ✅ |
| P5 | Pruebas de dominio, de casos de uso y de integración | ✅ |
| C1 | Campos nuevos en los environments y guía de GitHub Environments | ✅ |
| C2 | Logo e identidad visual | ✅ |

## Reglas de negocio implementadas

- Los estados de cocina avanzan en orden (Pendiente → En preparación → Listo → Entregado), sin saltos ni
  retrocesos; cada cambio guarda su hora para medir los tiempos de preparación.
- Un pedido solo se modifica o se cancela mientras está Pendiente; la mesa de un pedido no se cambia.
- Cada línea guarda el precio del momento: cambiar un precio después no altera los pedidos ya tomados.
- Un producto desactivado no se puede vender. Los productos no se borran, se desactivan.
- La venta solo se registra para pedidos Entregados; después el pedido queda cerrado.
- `estado_cocina` (preparación) y `estado_pedido` (Abierto, Pagado, Cancelado) son independientes.

Todas viven en la entidad `Pedido` del dominio; los servicios solo buscan los datos y guardan el resultado.

## Modelo de datos

| Tabla | Campos principales | Relación |
|---|---|---|
| `categorias` | id_categoria, nombre (único), activa | — |
| `productos` | id_producto, nombre, descripcion, precio, activo | muchos → 1 `categorias` |
| `pedidos` | id_pedido, numero_mesa, estado_cocina, estado_pedido, total, observaciones, fecha_creacion, fecha_inicio_preparacion, fecha_listo, fecha_entrega, fecha_cierre | muchos → 1 `usuarios` (mesero) |
| `detalle_pedidos` | id_detalle, cantidad, precio_unitario, subtotal, nota | muchos → 1 `pedidos` y → 1 `productos` |
| `ingredientes` | id_ingrediente, nombre, unidad_medida, stock_actual, stock_minimo, activo | — |
| `receta_ingredientes` | id_receta, cantidad | une `productos` e `ingredientes` |
| `movimientos_inventario` | id_movimiento, tipo, cantidad, motivo, fecha | → `ingredientes`, `usuarios`, `pedidos` |
| `alertas` | id_alerta, mensaje, prioridad, atendida, fechas | → `ingredientes` |

Los **roles** siguen siendo un valor fijo (`ADMIN`, `MESERO`, `COCINA`, `INVENTARIO`) en `usuarios.rol`, con
una restricción `CHECK` en la base de datos: el sistema no permite crear roles nuevos, así que una tabla
aparte no agrega funcionalidad.

## Contrato del API

| Método | Ruta | Acceso | Cuerpo |
|---|---|---|---|
| GET | `/api/categorias` | Con sesión | — |
| POST | `/api/categorias` | ADMIN | `{ nombre }` |
| GET | `/api/productos?soloActivos=&categoriaId=` | Con sesión | — |
| POST · PUT | `/api/productos` · `/api/productos/{id}` | ADMIN | `{ nombre, descripcion, precio, categoriaId }` |
| PATCH | `/api/productos/{id}/disponibilidad` | ADMIN | `{ activo }` |
| POST | `/api/pedidos` | MESERO, ADMIN | `{ numeroMesa, lineas: [{ productoId, cantidad, nota }], observaciones }` |
| GET | `/api/pedidos?mesa=&estadoCocina=&estadoPedido=` | MESERO, COCINA, ADMIN | — |
| GET | `/api/pedidos/{id}` | MESERO, COCINA, ADMIN | — |
| PUT | `/api/pedidos/{id}` | MESERO, ADMIN | igual que POST |
| PATCH | `/api/pedidos/{id}/cancelar` · `/api/pedidos/{id}/pagar` | MESERO, ADMIN | — |
| GET | `/api/cocina/comandas` | COCINA, ADMIN | — |
| PATCH | `/api/cocina/pedidos/{id}/estado` | COCINA, ADMIN | `{ estado }` |

Errores nuevos: **409** cuando la operación no se permite en el estado actual del pedido (por ejemplo, modificar
un pedido En preparación o saltar de Pendiente a Listo).

## Resultado

- 52 pruebas automáticas en verde: 28 nuevas en este sprint, entre ellas una prueba de integración que recorre
  todo el flujo por el API con tokens reales de mesero, cocina y administrador.
- Frontend revisado con el compilador de Angular en modo estricto (TypeScript y plantillas).

## Cómo probarlo

1. Arrancar el backend: en desarrollo se carga solo un menú de ejemplo (4 categorías y 9 productos).
2. Con el administrador, crear en **Usuarios** una cuenta MESERO y una COCINA.
3. Abrir dos navegadores (o uno normal y otro en incógnito): uno con el mesero en **Pedidos** y otro con la
   cocina en **Cocina**. Registrar un pedido y avanzar su estado desde la cocina; el mesero lo ve cambiar
   sin recargar y, cuando está Entregado, registra la venta.
