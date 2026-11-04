# Sprint 5 · RestoFlow AI

Responsable: **Brayan Estiven Sarmiento Vargas** (backend, frontend y documentación).

**Objetivo del sprint:** que RestoFlow no solo registre lo que pasa, sino que **anticipe**: analizar las ventas y el
consumo de inventario para decir qué comprar, qué demanda se espera mañana y qué está cambiando, y avisar solo
cuando un insumo se va a acabar.

En Jira es el **Sprint 5**: épica *Agente de Inteligencia Artificial (RestoFlow AI)* (RES-6), tareas RES-36 a RES-42,
más la prueba del agente con datos de prueba (RES-44). El despliegue en AWS EC2 (recomendación del comité) queda
para el Sprint 6.

## Qué es RestoFlow AI (y qué no es)

RestoFlow AI es un **agente de análisis basado en reglas y estadística simple**: promedios, tendencias y
comparaciones con el historial. No usa un modelo de lenguaje ni *deep learning* (este último está fuera del alcance
del proyecto).

| Decisión | Por qué |
|---|---|
| Reglas y estadística, no un modelo entrenado | Un restaurante pequeño tiene pocas semanas de datos: un modelo complejo no tendría con qué aprender. Los promedios funcionan desde la primera semana |
| **Explicable** | Cada recomendación dice de dónde sale ("alcanza para 2,4 días al ritmo actual"). El administrador puede confiar en ella y el docente puede verificar el cálculo |
| Sin servicios externos | No hay costos, claves ni datos del restaurante enviados a terceros, y funciona sin internet |
| Calculado al momento, no guardado | El análisis se arma con los datos actuales en cada consulta, así todas las copias del backend responden igual (factor VI de los 12 factores) |
| Días completos (hasta ayer) | Si se contara el día de hoy a medias, en la mañana el consumo y las ventas parecerían más bajos de lo que son |

## Tareas

| Jira | Tarea | Qué se hizo |
|---|---|---|
| RES-36 | RF-09 Analizar datos históricos de ventas | Ventas de las últimas 4 semanas: total de la semana, productos más vendidos, día más fuerte, hora pico y promedio por día de la semana |
| RES-37 | RF-10 Analizar consumo de productos e ingredientes | Consumo diario de cada insumo (salidas de los últimos 7 días ÷ 7) y días que alcanza el stock |
| RES-38 | Detectar tendencias | Cambio % de cada insumo, de cada producto y de las ventas frente a la semana anterior |
| RES-39 | RF-11 Generar recomendaciones | Compras con cantidad sugerida, aviso de más demanda mañana, productos que suben o bajan |
| RES-40 | Alertas automáticas | Tarea programada cada hora: alerta cuando un insumo alcanza para 3 días o menos; la cierra sola cuando vuelve a alcanzar |
| RES-41 | Predicción básica de demanda | Ventas, pedidos y productos esperados para mañana: promedio de los mismos días de la semana de las últimas 4 semanas |
| RES-42 | Interfaz del agente | Pantalla **RestoFlow AI** y tarjeta en el Dashboard |
| RES-44 | Pruebas con datos de prueba | 16 pruebas unitarias con datos inventados, 1 de integración y 12 de Jest |

## Cómo decide (fórmulas)

**Insumos (`AnalizadorAbastecimiento`)**

| Cálculo | Fórmula |
|---|---|
| Consumo diario | salidas de los últimos 7 días completos ÷ 7 (ventas y mermas: las dos gastan el insumo) |
| Tendencia | (semana actual − semana anterior) ÷ semana anterior × 100 |
| Alcanza para | stock actual ÷ consumo diario |
| Prioridad | **ALTA** si alcanza para 3 días o menos, o ya está en su mínimo · **MEDIA** si para 7 días o menos |
| Cantidad sugerida | consumo diario × 7 × (1 + tendencia si sube, máximo el doble) + stock mínimo − stock actual |

Ejemplo: el arroz tiene 3.000 g, mínimo 1.000 g, y esta semana se usaron 7.000 g (el doble que la anterior).
Consumo diario 1.000 g → alcanza para 3 días → prioridad ALTA → comprar 1.000 × 7 × 2 + 1.000 − 3.000 = **12.000 g**.

**Ventas (`AnalizadorVentas`)**

- **Día más fuerte:** el día de la semana con el mayor promedio de ventas.
- **Hora pico:** la hora en que se hacen más pedidos.
- **Predicción de mañana:** si mañana es sábado, se promedian los sábados de las últimas 4 semanas (ventas, número
  de pedidos y unidades de cada producto). Si todavía no hay ningún sábado, se usa el promedio de la última semana.
- **Datos suficientes:** desde 7 días con ventas. Antes, RestoFlow AI avisa que "está aprendiendo".

**Recomendaciones (`GeneradorRecomendaciones`)**

| Tipo | Cuándo aparece | Prioridad |
|---|---|---|
| Compra | Un insumo alcanza para 7 días o menos | La del insumo (ALTA o MEDIA) |
| Demanda | Mañana se espera 20 % más de ventas que un día promedio | MEDIA |
| Tendencia | Un insumo que aún alcanza sube su consumo 20 % o más | BAJA |
| Producto | Un producto sube o baja 30 % o más (con al menos 5 unidades) | BAJA |
| Información | Hay menos de 7 días con ventas | BAJA |

## Alertas automáticas (RES-40)

- `RestoFlowAIProgramado` es un **adaptador de entrada por reloj**: igual que un controlador recibe peticiones, este
  se activa al minuto 5 de cada hora y llama al caso de uso `GenerarAlertasIAUseCase`.
- Crea una alerta MEDIA que empieza por **"RestoFlow AI:"** cuando un insumo alcanza para 3 días o menos pero todavía
  está sobre su mínimo. Ejemplo: *"RestoFlow AI: al ritmo actual, Plátano verde alcanza para 2,4 días. Sugerido:
  comprar 62 und."*
- **No se duplica:** si el insumo ya tiene una alerta pendiente (de RestoFlow AI o de stock), no crea otra; el
  resultado lo cuenta como "ya avisado". Si el insumo ya llegó a su mínimo, avisa la alerta de stock (RF-07).
- **Se cierra sola** cuando el insumo vuelve a alcanzar para más de 3 días (por ejemplo, después de una compra). El
  control de stock (`ControlAlertas`) ya no cierra estas alertas en cada venta, porque una venta no resuelve el
  problema.
- Se ven en **Inventario → Alertas**, donde el personal ya revisa las demás. La pantalla de RestoFlow AI tiene un botón
  **Generar alertas ahora** para no esperar la hora.
- Configuración: `IA_ALERTAS_PROGRAMADAS` (true/false) e `IA_CRON` (por defecto `0 5 * * * *`).

## Arquitectura (lo nuevo)

Sigue el mismo camino hexagonal de los sprints anteriores. El motor está en el dominio, en Java puro:

| Capa | Archivos |
|---|---|
| Dominio (`domain/ia`) | `AnalizadorAbastecimiento`, `AnalizadorVentas`, `GeneradorRecomendaciones` y los resultados `PronosticoInsumo`, `AnalisisVentas`, `Recomendacion` |
| Dominio (cambio) | `Alerta.esDeRestoFlowAI()` y el prefijo de sus mensajes |
| Puertos de entrada | `ConsultarRestoFlowAIUseCase` (el análisis) y `GenerarAlertasIAUseCase` (las alertas) |
| Servicio | `RestoFlowAIService`: reúne los datos con los puertos que ya existían (pedidos, insumos, movimientos y alertas) y llama al dominio |
| Adaptadores de entrada | `RestoFlowAIController` (`GET /api/restoflow-ai/analisis`, `POST /api/restoflow-ai/alertas`) y `RestoFlowAIProgramado` (cada hora) |
| Seguridad | `/api/restoflow-ai/**` solo para ADMIN e INVENTARIO |
| Datos de ejemplo | `HistorialDemoConfig` (opcional, ver abajo) |

No se agregaron tablas ni se cambió el script SQL: RestoFlow AI lee los pedidos y movimientos que ya se guardan, y
sus alertas usan la tabla `alertas`.

**SOLID en este sprint:**

- **S:** cada clase del motor hace una cosa (insumos, ventas, recomendaciones); el servicio solo reúne datos.
- **O:** se agregó toda la funcionalidad sin modificar los puertos de salida existentes.
- **I:** dos puertos de entrada pequeños (1 operación cada uno) en lugar de uno grande.
- **D:** el motor no conoce la base de datos ni Spring; `ArquitecturaTest` lo verifica.

## Datos de ejemplo para demostrar (opcional)

Para que RestoFlow AI tenga qué analizar desde el primer día, con `IA_HISTORIAL_DEMO=true` el backend genera al
arrancar **3 semanas de ventas de ejemplo**:

- No escribe directo en la base de datos: usa los **mismos casos de uso** que el mesero y la cocina (registrar,
  preparar, entregar y cobrar), con un reloj que "viaja" a cada día del pasado. Así cada venta descuenta su inventario
  como una real. Las compras de insumos se registran como entradas.
- Fines de semana con más pedidos y la última semana con más pedidos que las anteriores.
- Los 2 últimos días no se compra, para que algunos insumos queden bajos y aparezcan recomendaciones.
- No hace nada si ya hay más de 30 ventas en las últimas 3 semanas, así que se puede dejar activado sin duplicar datos.
- Corre en segundo plano: el backend queda listo mientras termina (unos segundos).

```powershell
# Docker (con la base de datos local): agregar al .env la línea IA_HISTORIAL_DEMO=true y levantar
docker compose -f docker-compose.yml -f docker-compose.bd-local.yml up -d --build

# Modo local
$env:IA_HISTORIAL_DEMO = "true"
cd backend
.\mvnw.cmd spring-boot:run
```

Después de la demostración se puede volver a `false`; los datos generados se quedan como ventas normales.

## Pantallas

| Pantalla | Ruta | Roles | Qué muestra |
|---|---|---|---|
| **RestoFlow AI** (nueva) | `/restoflow-ai` | ADMIN, INVENTARIO | Ventas de 7 días con su tendencia, día más fuerte, hora pico e insumos por comprar; recomendaciones con filtro por prioridad; tabla de insumos (stock, consumo diario, tendencia, alcanza para, prioridad y cantidad sugerida); predicción de mañana; ventas promedio por día; productos de la semana; "Cómo decide" con las fórmulas |
| **Dashboard** | `/inicio` | ADMIN | La tarjeta RestoFlow AI deja de ser "Pronto": muestra las 3 recomendaciones más importantes y enlaza al análisis completo |
| Menú | | | *RestoFlow AI* deja de ser "Pronto" para ADMIN e INVENTARIO |

## Pruebas

| Prueba | Qué comprueba |
|---|---|
| `RestoFlowAIDominioTest` (12) | Fórmulas de consumo, tendencia, cobertura, prioridad y compra sugerida; análisis de 4 semanas de ventas; predicción del viernes; textos y orden de las recomendaciones |
| `RestoFlowAIServiceTest` (4) | Con dobles en memoria: análisis ordenado por urgencia, alerta anticipada sin duplicarse, una venta no la cierra y una compra sí, y no se duplica con la alerta de stock |
| `RestoFlowAIIntegracionTest` (1) | API completo: ADMIN e INVENTARIO entran, MESERO recibe 403 y sin sesión 401 |
| `restoflow-ai.test.ts` (12, Jest) | Textos de tendencia, cobertura y hora; gráfico por día; conteo y selección de recomendaciones |

Total del proyecto: **223 pruebas JUnit** y **92 pruebas Jest**.

## Cómo probarlo

1. Levantar RestoFlow con `IA_HISTORIAL_DEMO=true` (ver arriba) y entrar como administrador.
2. **Dashboard:** la tarjeta RestoFlow AI muestra las 3 recomendaciones más importantes.
3. **RestoFlow AI:** revisar los indicadores, la predicción de mañana y la tabla de insumos. Filtrar por prioridad Alta.
4. Pulsar **Generar alertas ahora** y abrir **Inventario → Alertas**: aparece la alerta "RestoFlow AI: ...".
5. Registrar una entrada grande de ese insumo, volver a RestoFlow AI y generar alertas: la alerta se cierra sola.
6. En Swagger: **RestoFlow AI → GET /api/restoflow-ai/analisis** devuelve todo el análisis en JSON.
