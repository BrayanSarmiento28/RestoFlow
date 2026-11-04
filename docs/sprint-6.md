# Sprint 6 · Recomendaciones del comité

**Proyecto Integrador · Universidad de Cundinamarca · 2026-II**
**Autor:** Brayan Estiven Sarmiento Vargas

En el comité del 30 de octubre el docente pidió:

| # | Recomendación | Cómo se resuelve | Estado |
|---|---------------|------------------|--------|
| 1 | Implementar logs que se guarden en la base de datos | Registro de actividad: cada petición queda en la tabla `registro_peticiones` de SQL Server | **Hecho** (esta parte) |
| 2 | Validar API externa | RestoFlow AI integrado en toda la plataforma: avisos por rol (cocina, mesero, inventario, admin) | Parte 2 |
| 3 | Validar lógica de la aplicación | Pruebas de integración de punta a punta de los flujos del negocio | Parte 3 |
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
