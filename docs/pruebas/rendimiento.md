# Prueba de rendimiento · cambio de estado del pedido (RES-28)

**Requerimiento no funcional:** el estado de un pedido debe actualizarse en máximo **3 segundos**.
**Fecha de la prueba:** 15 de octubre de 2026 · **Responsable:** Brayan Estiven Sarmiento Vargas

## Cómo se midió

Script [`medir-rendimiento.ps1`](medir-rendimiento.ps1) (PowerShell), ejecutado contra la solución completa
levantada con Docker Compose (SQL Server 2022, backend Spring Boot y frontend Nginx) en el computador del
desarrollador (Windows con Docker Desktop).

1. Inicia sesión como administrador y toma un producto disponible del menú.
2. Hace 2 ciclos de calentamiento que **no se cuentan** (la primera petición de cada tipo es más lenta porque la
   máquina virtual de Java todavía está compilando el código).
3. Repite **20 veces** el ciclo completo de un pedido y cronometra cada petición HTTP desde que sale hasta que
   llega la respuesta: crear pedido → consultar comandas → En preparación → Listo → consultar el pedido como
   mesero → Entregado.
4. Calcula promedio, mediana, percentil 95 (P95) y máximo de cada operación y los compara con 3000 ms.

El detalle de las 120 peticiones está en [`resultados-rendimiento.csv`](resultados-rendimiento.csv).

## Resultados

| Operación | Veces | Promedio (ms) | Mediana (ms) | P95 (ms) | Máximo (ms) | Cumple |
|---|---:|---:|---:|---:|---:|:---:|
| Crear pedido (mesero) | 20 | 42,1 | 37,0 | 72,7 | 78,5 | Sí |
| Consultar comandas (cocina) | 20 | 20,4 | 19,5 | 27,1 | 27,1 | Sí |
| Cambiar a En preparación | 20 | 44,5 | 38,2 | 71,0 | 123,2 | Sí |
| Cambiar a Listo | 20 | 35,1 | 33,6 | 41,9 | 45,1 | Sí |
| Consultar pedido (mesero) | 20 | 21,9 | 20,8 | 26,6 | 26,6 | Sí |
| Cambiar a Entregado | 20 | 37,1 | 34,9 | 43,3 | 76,1 | Sí |

**Todas las operaciones respondieron en menos de 3000 ms.** El cambio de estado más lento tardó 123,2 ms, es
decir, unas 24 veces por debajo del límite.

## Tiempo hasta que la otra pantalla ve el cambio

Que el servidor responda rápido no basta: cuando cocina marca un pedido como *Listo*, el mesero debe verlo en
su pantalla. Las pantallas de cocina y de pedidos se consultan solas cada cierto tiempo (`refrescoCocinaMs` y
`refrescoPedidosMs` en `frontend/src/environments`).

| | Antes | Después |
|---|---|---|
| Pantalla de cocina | 5 s | 2,5 s |
| Lista de pedidos del mesero | 10 s (15 s en producción) | 2,5 s |

Con los valores anteriores el mesero podía tardar hasta 15 segundos en ver un pedido listo, y el requerimiento
**no se cumplía de punta a punta**. Con 2,5 s, el peor caso es:

> 2500 ms (espera hasta la siguiente consulta) + 123 ms (cambio de estado más lento) + 27 ms (consulta más lenta)
> ≈ **2,65 s**, por debajo de los 3 s.

Quien hace el cambio lo ve de inmediato: la pantalla de cocina vuelve a consultar las comandas apenas el
servidor confirma el nuevo estado.

**Costo:** cada pantalla abierta hace una consulta pequeña cada 2,5 s (unos 20 ms en el servidor). Para un
restaurante con pocas pantallas abiertas es una carga muy baja.

## Cómo repetir la prueba

```powershell
docker compose up -d --build        # en la raíz del proyecto; esperar a que el backend esté "healthy"
cd docs\pruebas
.\medir-rendimiento.ps1             # o: .\medir-rendimiento.ps1 -Repeticiones 50
```

Los pedidos de prueba se crean en la **mesa 99** con la observación "Prueba de rendimiento RES-28".

## Límites de esta prueba

- Mide un solo usuario a la vez en el mismo computador (sin la latencia de una red real). Una prueba de carga con
  muchos usuarios simultáneos queda para el sprint de pruebas (RES-43 a RES-49).
- El tiempo de pintar la pantalla en el navegador no se incluye; en Angular es de pocos milisegundos.
