# Pruebas de carga y estrés con Apache JMeter (RES-56)

La prueba de rendimiento de [RES-28](rendimiento.md) midió cuánto tarda cada operación con **un solo usuario**.
Estas pruebas miden lo que pasa cuando **muchas personas usan RestoFlow al mismo tiempo**:

| Prueba | Pregunta que responde | Usuarios a la vez | Duración |
|---|---|---:|---:|
| **Humo** | ¿Funcionan la prueba y el sistema de punta a punta? | 5 (una vuelta) | ~20 s |
| **Carga** | ¿Aguanta la hora pico con margen, cumpliendo los 3 s? | 100 | ~6 min |
| **Estrés** | ¿En qué punto se degrada y se recupera? | sube hasta 600, luego 5 | ~14 min |

**Criterio de aceptación** (requerimiento no funcional del proyecto): en la prueba de carga, el **percentil 95** de
**cada operación** debe estar por debajo de **3 000 ms**, con menos de **1 %** de peticiones con error. El P95 indica
que 95 de cada 100 peticiones respondieron en ese tiempo o menos. Es más exigente que el promedio, porque no deja que
unas pocas peticiones rápidas escondan a las lentas. El informe de JMeter también calcula el índice **Apdex**, con
"satisfecho" hasta 500 ms y "tolerable" hasta 3 000 ms.

## Herramienta: Apache JMeter 5.6.3

[Apache JMeter](https://jmeter.apache.org) es la herramienta de código abierto de la Apache Software Foundation para
pruebas de carga. Es la que pide el curso y la más usada en la industria.

- **Plan de prueba:** [`jmeter/restoflow.jmx`](jmeter/restoflow.jmx). Se abre en la interfaz gráfica de JMeter y
  queda en el repositorio, así la prueba se puede repetir igual cuando se quiera.
- **Perfiles:** el mismo plan sirve para las tres pruebas. Lo que cambia (usuarios, subida y duración) está en
  `humo.properties`, `carga.properties` y `estres.properties`.
- **Ejecución:** [`jmeter/ejecutar-jmeter.ps1`](jmeter/ejecutar-jmeter.ps1). La primera vez descarga JMeter desde
  Apache y verifica su firma SHA-512. Luego ejecuta la prueba y genera el **informe HTML de JMeter**, con gráficas de
  tiempos, usuarios activos y peticiones por segundo a lo largo del tiempo.
- **Ver en vivo:** con `-Gui` se abre la interfaz gráfica con el plan cargado. Al pulsar el botón verde se ven el
  *Resumen en vivo* (por operación) y el *Gráfico de tiempos de respuesta*. Los resultados oficiales se toman en modo
  consola, sin `-Gui`, como recomienda Apache: la interfaz gráfica consume CPU y altera la medición. En modo consola,
  cada 10 segundos aparece una línea `summary +` con el avance.

## Qué hace cada grupo de usuarios del plan

| Grupo de hilos | Qué hace | Carga | Estrés |
|---|---|---:|---:|
| **Preparación** (*setUp*) | Comprueba que el API responde, inicia sesión como administrador, lee el menú y crea dos cuentas temporales (mesero y cocina). Si algo falla, la prueba se detiene | 1 | 1 |
| **Pedidos completos** | El mesero ve el menú y crea el pedido → la cocina lo pasa a En preparación, Listo y Entregado → el administrador cobra (se descuenta inventario por receta) | 50 | 300 |
| **Pantalla de Pedidos** | Lista de pedidos activos y mesas ocupadas cada 2,5 s, como el frontend | 26 | 160 |
| **Pantalla de Cocina** | Comandas cada 2,5 s | 10 | 60 |
| **Dashboard** | Resumen del día cada 5 s y, a veces, la campana de avisos | 7 | 40 |
| **Inicio de sesión** | Cambio de turno: un inicio de sesión cada 10 a 20 s. BCrypt hace que sea la operación más costosa | 7 | 40 |
| **Recuperación** | Solo en estrés: después del pico, 5 usuarios durante 2 minutos, para ver si los tiempos vuelven a la normalidad | 0 | 5 |
| **Limpieza** (*tearDown*) | Cierra los pedidos de prueba que quedaron abiertos y desactiva las cuentas temporales | 1 | 1 |

- **Pausas:** entre una acción y otra hay pausas (*Flow Control Action*) de 1 a 5 segundos, el tiempo que una persona
  tarda en tomar el pedido o en cocinar. Sin ellas la prueba no representaría el uso real del restaurante.
- **Subida:** en carga, los usuarios llegan durante 1 minuto. En estrés, durante 10 minutos, hasta 6 veces la prueba
  de carga.
- **Por qué 100 usuarios en la carga:** un restaurante mediano tiene unas 30 personas usando el sistema en la hora
  pico. Con 100 (50 mesas atendidas a la vez) se comprueba que RestoFlow cumple los 3 s con más de 3 veces de margen,
  lo que equivale a un restaurante grande o a varias sedes. Los números se cambian en los `.properties`, sin tocar el
  plan.
- **Reglas del negocio:** cada usuario virtual atiende **su propia mesa** (101, 102, …), porque una mesa ocupada no
  recibe otro pedido. Si una vuelta anterior dejó su mesa ocupada (por ejemplo, por un cobro con error), el plan cierra
  ese pedido para seguir. Esa petición sigue contando como error en el informe.
- **Seguridad:** la contraseña del administrador llega por una variable de entorno que pone el guion de PowerShell.
  No se escribe en el plan, en los `.properties` ni en la línea de comandos. Las cuentas temporales tienen una
  contraseña aleatoria que solo existe durante la prueba.

## Datos que crea (y dónde se puede ejecutar)

La prueba crea pedidos reales con la observación "Prueba de carga", registra ventas y descuenta inventario. Por eso
**nunca se ejecuta contra una base de datos con datos reales**. El guion avisa y pide confirmación si detecta que
RestoFlow en el PC no está usando la base de datos de Docker.

| Dónde | Cómo |
|---|---|
| **PC, base de Docker** (ensayo) | `docker compose down` y luego `docker compose -f docker-compose.yml up -d --build` |
| **AWS, pre-producción** (resultado oficial) | `-Servidor IP-DEL-SERVIDOR -Puerto 80` (ver [despliegue en AWS](../despliegue-aws.md)) |

## Cómo ejecutarla

```powershell
cd C:\Users\BRAYAN\Documents\RestoFlow\RestoFlow
.\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba humo
.\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba carga
.\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba estres
```

| Resultado (en `docs/pruebas/jmeter/resultados/`) | Para qué | ¿Se sube a Git? |
|---|---|:---:|
| `<prueba>-<fecha>/index.html` | Informe HTML de JMeter: estadísticas, Apdex y gráficas en el tiempo | No: incluye librerías de terceros y se regenera desde el `.jtl` |
| `<prueba>-<fecha>.md` | Tabla por operación con P95, errores y si cumple 3 s | Sí |
| `<prueba>-<fecha>.jtl` | Cada petición con su hora (puede pesar decenas de MB) | No |

## Primera medición: el error de concurrencia (RES-45)

Antes de JMeter, los mismos recorridos se midieron con k6 (9 de noviembre, 30 usuarios, RestoFlow en Docker en el
PC). Esa primera medición **no cumplió**. La mayor parte del tiempo el P95 estaba entre 30 y 60 ms, pero en 6
momentos todo el sistema se detuvo de 5 a 11 segundos, y 18 cobros terminaron en error 500. Los registros del
backend mostraron **23 deadlocks de SQL Server**: varios cobros simultáneos descontaban el mismo insumo, y esos
descuentos se podían perder.

Se corrigió en RES-45 con dos cambios: reservar el insumo antes de cambiar su stock, y activar las lecturas sin
bloqueo (`READ_COMMITTED_SNAPSHOT`). Además se agregó una prueba de integración con 10 cobros simultáneos. El detalle
está en [`docs/sprint-6.md` §6.8](../sprint-6.md).

Después, el curso pidió JMeter, así que las pruebas oficiales se hicieron con JMeter (las tablas de abajo). k6 se
retiró del proyecto, para no mantener dos herramientas que hacen lo mismo.

## Resultados

### PC local (base de Docker), después de RES-45 · 10 de noviembre

RestoFlow en Docker Desktop con su propia base de datos (SQL Server 2022 Developer), y JMeter en el mismo portátil.
Resumen: [`jmeter/resultados/carga-2026-11-10-15-38.md`](jmeter/resultados/carga-2026-11-10-15-38.md). El informe HTML
con las gráficas queda en el computador donde se ejecutó (`resultados/carga-2026-11-10-15-38/index.html`).

**Humo** (1 usuario por recorrido): 11 peticiones, 0 errores, todas por debajo de 700 ms.

**Carga: 100 usuarios a la vez durante 5 minutos → CUMPLE.**

- 17 483 peticiones (48,6 por segundo), **0 errores** y **1 442 ventas completas** (pedido → cocina → cobro).
- Tiempo global: promedio 41 ms, **P95 150 ms**, máximo 872 ms.
- La limpieza cerró los 28 pedidos que quedaron a medias al terminar y desactivó las cuentas temporales.
- No hubo ninguna mesa trabada: ningún pedido tuvo que liberarse por un cobro fallido.

| Operación | Peticiones | Promedio (ms) | Mediana (ms) | P95 (ms) | Máximo (ms) | Errores | Cumple 3 s |
|---|---:|---:|---:|---:|---:|---:|:---:|
| iniciar sesión | 152 | 157,4 | 155,0 | 179,0 | 250,0 | 0,00 % | Sí |
| ver menú | 1 492 | 13,5 | 12,0 | 20,0 | 228,0 | 0,00 % | Sí |
| crear pedido | 1 470 | 43,0 | 38,0 | 68,0 | 313,0 | 0,00 % | Sí |
| cocina: en preparación | 1 464 | 31,9 | 26,0 | 52,0 | 346,0 | 0,00 % | Sí |
| cocina: listo | 1 457 | 31,7 | 27,0 | 53,0 | 318,0 | 0,00 % | Sí |
| cocina: entregado | 1 446 | 32,2 | 27,0 | 56,0 | 264,0 | 0,00 % | Sí |
| cobrar | 1 442 | 111,6 | 92,0 | 238,9 | 646,0 | 0,00 % | Sí |
| pantalla cocina | 1 300 | 46,1 | 41,0 | 78,0 | 293,0 | 0,00 % | Sí |
| lista de pedidos | 3 378 | 25,5 | 21,0 | 33,0 | 872,0 | 0,00 % | Sí |
| mesas ocupadas | 3 377 | 23,2 | 19,0 | 30,0 | 551,0 | 0,00 % | Sí |
| panel administrador | 448 | 176,7 | 163,0 | 269,1 | 796,0 | 0,00 % | Sí |
| avisos | 57 | 226,3 | 191,0 | 495,1 | 759,0 | 0,00 % | Sí |

**Antes y después de la corrección de concurrencia (RES-45):**

| | Antes (9 nov, k6) | Después (10 nov, JMeter) |
|---|---|---|
| Usuarios a la vez | 30 | **100** (3,3 veces más) |
| Errores | 18 cobros con error 500 (deadlocks) | **0** |
| Congelamientos | 6, de 5 a 11 s | **ninguno** (máximo 872 ms) |
| P95 de cobrar | más de 3 000 ms | **239 ms** |
| ¿Cumple 3 s? | No | **Sí, en todas las operaciones** |

**Qué se lee del resultado:**
- Cobrar (239 ms de P95) es la operación de negocio más pesada: en una sola transacción descuenta el inventario de
  cada insumo de la receta, reservándolo (RES-45), y revisa sus alertas.
- El Dashboard (269 ms) y los avisos (495 ms) calculan sobre todos los pedidos del día.
- Iniciar sesión (179 ms) es lento *a propósito*: BCrypt está diseñado para que adivinar contraseñas sea costoso.
- Todo queda muy por debajo del límite de 3 s, aun con más de 3 veces la hora pico de un restaurante mediano.

### Servidor AWS · pre-producción (RES-57)

*Pendiente de ejecutar.*
