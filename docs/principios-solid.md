# RestoFlow y los principios SOLID

**Proyecto Integrador · Universidad de Cundinamarca · 2026-II**
**Autor:** Brayan Estiven Sarmiento Vargas

SOLID son cinco principios de diseño orientado a objetos (Robert C. Martin) para que el código sea fácil de
entender, de probar y de cambiar sin romper lo que ya funciona:

| Letra | Principio | En una frase |
|-------|-----------|--------------|
| **S** | Responsabilidad única (*Single Responsibility*) | Cada clase tiene una sola razón para cambiar |
| **O** | Abierto/cerrado (*Open/Closed*) | Se agrega comportamiento nuevo sin modificar el que ya funciona |
| **L** | Sustitución de Liskov (*Liskov Substitution*) | Cualquier implementación de una interfaz puede reemplazar a otra sin romper nada |
| **I** | Segregación de interfaces (*Interface Segregation*) | Interfaces pequeñas y específicas, no una gigante |
| **D** | Inversión de dependencias (*Dependency Inversion*) | Las reglas del negocio dependen de abstracciones, no de detalles técnicos |

En RestoFlow, SOLID está implementado en la **arquitectura hexagonal** (puertos y adaptadores) y **verificado con
pruebas automáticas**: `backend/src/test/java/com/udec/restoflow/arquitectura/ArquitecturaTest.java`. Si alguien rompe
una regla, la prueba falla en `mvn verify` y en GitHub Actions.

```
          ┌──────────────── infraestructura (detalles: Spring, JPA, HTTP, Gmail) ────────────────┐
          │   Controladores REST                                      Adaptadores de salida      │
          │   (adapter/in/web)                                        (persistence, mail,        │
          │        │                                                   externo, security)        │
          │        ▼  usa                                                    ▲ implementa        │
          │   ┌──────────── aplicación (casos de uso) ─────────────┐         │                   │
          │   │  port/in  ◄── implementa ──  service  ── usa ──►  port/out ──┘                   │
          │   │  (interfaces)                                     (interfaces)                   │
          │   │                     ┌─── dominio ───┐                                            │
          │   │                     │ Java puro:    │                                            │
          │   │                     │ reglas del    │                                            │
          │   │                     │ restaurante   │                                            │
          │   │                     └───────────────┘                                            │
          │   └────────────────────────────────────────────────────┘                             │
          └──────────────────────────────────────────────────────────────────────────────────────┘
           Las flechas de dependencia siempre apuntan hacia adentro (hacia el dominio).
```

---

## S · Responsabilidad única

**Una clase, una razón para cambiar.**

| Dónde | Cómo se cumple |
|-------|----------------|
| `application/service/*Service.java` | **Un servicio por grupo de casos de uso**: `IniciarSesionService` (HU-01), `RecuperarContrasenaService` (HU-02), `GestionarPedidosService` (RF-02), `GestionarCocinaService` (RF-03), `DescontarInventarioService` (RF-06)… Cambiar cómo se cobra un pedido no toca el inicio de sesión. |
| `domain/model/Pedido.java`, `Ingrediente.java`, `Usuario.java` | Las **reglas del negocio** viven en la entidad del dominio, por ejemplo cuándo se puede cancelar o cobrar un pedido. El servicio solo busca los datos, llama a la regla y guarda. |
| `application/service/ControlAlertas.java` | Solo decide **cuándo crear o cerrar alertas**. Lo reutilizan el inventario manual y el descuento por venta. |
| `application/service/Auditoria.java` | Solo escribe los **eventos de auditoría**. |
| `infrastructure/logging/RegistroPeticionesFilter.java` | Solo registra **una línea por petición**. |
| `domain/model/Ventas.java` (**nuevo**) | Solo hace los **cálculos de ventas** (total y ticket promedio). |
| `infrastructure/adapter/in/web/GlobalExceptionHandler.java` | Solo **traduce errores a respuestas HTTP**. Los controladores no tienen `try/catch`. |
| Frontend: `core/services/*.service.ts` | **Un servicio por recurso del API**: `pedido`, `inventario`, `catalogo`, `panel`, `usuario`, `auth`. |
| Frontend: `core/util/*.ts` | La lógica sale de los componentes, que solo pintan la pantalla: `carrito.ts` (pedido en construcción), `inventario-calculos.ts`, `sesion.ts`, `acceso.ts`. |

**Mejora aplicada:** `ConsultarHistorialService` usaba los métodos `sumar()` y `promedio()` **de otro servicio**
(`ConsultarPanelService`). Así, un cambio en el panel podía romper el historial. Se movieron a su propia clase del
dominio, `Ventas`, que no depende de nada y tiene su prueba (`VentasTest`).

---

## O · Abierto/cerrado

**Abierto para extender, cerrado para modificar:** se agrega comportamiento nuevo con clases nuevas, no editando las
que funcionan.

| Dónde | Cómo se cumple |
|-------|----------------|
| `application/port/out/EnviarCorreoPort.java` | Dos implementaciones: `CorreoConsolaAdapter` (desarrollo) y `GmailCorreoAdapter` (producción). Se elige con **una variable** (`CORREO_MODO`). Para enviar por WhatsApp se escribe un adaptador nuevo y **no se modifica** `RecuperarContrasenaService`. |
| `application/port/out/CatalogoExternoPort.java` | Hoy lo implementa `OpenFoodFactsAdapter`. Otro catálogo de productos sería un adaptador nuevo; el caso de uso y la pantalla no cambian. |
| `GlobalExceptionHandler` + `ServicioExternoNoDisponibleException` | Cualquier servicio externo nuevo hereda de esa excepción y responde 503 **sin agregar código** al manejador de errores (así quedó el correo, ver principio D). |
| `RestoFlowProperties.java` | Una opción de configuración nueva es un campo nuevo; el resto del código no cambia. |
| Frontend: `app-module.ts` → `HTTP_INTERCEPTORS` (`multi: true`) | Se agregan interceptores (por ejemplo, uno de "cargando") **sin tocar** los servicios ni el `AuthInterceptor`. |
| Frontend: `rolGuard` + `data: { roles: [...] }` en `app-routing-module.ts` | Una pantalla nueva con permisos solo agrega su ruta; el guard no se modifica. |

**Prueba:** `ArquitecturaTest.correoIntercambiable()` comprueba que el correo tiene las dos implementaciones del mismo
puerto.

---

## L · Sustitución de Liskov

**Cualquier implementación de una interfaz puede reemplazar a otra** y el programa sigue funcionando bien.

| Dónde | Cómo se cumple |
|-------|----------------|
| `CorreoConsolaAdapter` ↔ `GmailCorreoAdapter` | Los dos cumplen el mismo contrato de `EnviarCorreoPort`. `RecuperarContrasenaService` funciona igual con cualquiera. |
| `*PersistenceAdapter` (SQL Server) ↔ dobles en memoria de las pruebas | En las pruebas unitarias, `Dobles.java`, `DoblesPedidos.java` y `DoblesInventario.java` implementan los mismos puertos que JPA, guardando en un `Map`. **Los mismos servicios** funcionan con ellos sin saberlo. Así corren más de 100 pruebas sin base de datos en pocos segundos. |
| `OpenFoodFactsAdapter` ↔ `CatalogoFalso` (`BuscarProductoExternoServiceTest`) | El servicio funciona igual con la API real o con el doble. El doble también simula "servicio caído", y el contrato (lanzar `ServicioExternoNoDisponibleException`) se respeta en los dos. |
| `CorreoNoEnviadoException` **es una** `ServicioExternoNoDisponibleException` | Donde se espera la general, la del correo la reemplaza y el comportamiento (503) es el mismo. |
| Frontend: `AlmacenSesion` con `localStorage` ↔ `AlmacenEnMemoria` (`sesion.test.ts`) | La misma clase funciona con el almacenamiento real del navegador o con el falso de las pruebas de Jest. |

---

## I · Segregación de interfaces

**Interfaces pequeñas y específicas:** ninguna clase debe depender de métodos que no usa.

| Dónde | Cómo se cumple |
|-------|----------------|
| `application/port/in/*UseCase.java` (13 interfaces) | **Un puerto por grupo de casos de uso**, de 1 a 9 operaciones. `AuthController` solo conoce los tres puertos que usa (`IniciarSesionUseCase` con 1 método, `RecuperarContrasenaUseCase` con 2 y `GestionarCuentasUseCase` con 3), no todo el sistema. |
| `application/port/out/*Port.java` (13 interfaces) | **Un puerto por necesidad**: `CifradoContrasenaPort` (cifrar y comparar), `GeneradorTokenPort`, `EnviarCorreoPort` (1 método), `CatalogoExternoPort` (1 método), y un repositorio por entidad con solo las consultas que se usan. |
| Frontend: `AlmacenClaveValor` (`core/util/sesion.ts`) | La sesión no depende de toda la interfaz `Storage` del navegador, solo de 3 métodos: `getItem`, `setItem` y `removeItem`. |
| Frontend: `ErrorHttp` (`core/services/errores.ts`) | `mensajeDeError` no pide un `HttpErrorResponse` completo, solo `status` y `error`. |

**Prueba:** `ArquitecturaTest.puertosSonInterfacesPequenas()` comprueba que los 26 puertos son interfaces y que
ninguno pasa de 10 operaciones.

---

## D · Inversión de dependencias

**Los módulos de alto nivel (reglas del negocio) no dependen de los de bajo nivel (base de datos, HTTP, correo):
ambos dependen de abstracciones.**

| Dónde | Cómo se cumple |
|-------|----------------|
| `domain/` | **Java puro.** No importa Spring, JPA, Jackson ni nada de otras capas. Las reglas del restaurante se pueden probar y reutilizar sin ningún framework. |
| `application/service/` | Los servicios reciben **interfaces** (`PedidoRepositoryPort`, `EnviarCorreoPort`…) en el constructor y nunca crean ni conocen la implementación. |
| `infrastructure/config/BeanConfig.java` | Es el **único lugar** que conecta cada caso de uso con sus adaptadores (inyección de dependencias). Por eso los servicios no llevan `@Service` ni `@Autowired`. |
| `infrastructure/adapter/in/web/*Controller.java` | Los controladores dependen de los **puertos de entrada** (`GestionarPedidosUseCase`), no de `GestionarPedidosService`. |
| `ServicioExternoNoDisponibleException` (**mejora aplicada**) | Antes, `GlobalExceptionHandler` (web) importaba `CorreoNoEnviadoException`, que vive en el adaptador de correo: un adaptador de entrada dependía de uno de salida. Ahora la del correo hereda de la excepción general del dominio, y la web solo conoce la abstracción. La prueba de arquitectura detectó este acoplamiento. |
| Frontend: `AlmacenSesion` recibe sus almacenes y su reloj | `AuthService` le pasa `localStorage` y `sessionStorage`; las pruebas le pasan almacenes en memoria y un reloj fijo. |
| Frontend: Angular `inject()` | Componentes, guards e interceptor reciben sus servicios por inyección de dependencias, nunca con `new`. |

**Excepciones conscientes** (documentadas en la prueba):

- `@Transactional` (Spring) en los servicios marca dónde empieza y termina cada transacción; por ejemplo, una venta y
  su descuento de inventario quedan juntos o no queda ninguno.
- SLF4J en `Auditoria` es una **fachada** de logs (una interfaz), no una implementación.

**Pruebas:**

- `ArquitecturaTest.dominioSinDependencias()`
- `aplicacionNoConoceInfraestructura()`
- `controladoresUsanPuertosDeEntrada()`
- `serviciosImplementanCasosDeUso()`
- `adaptadoresImplementanPuertosDeSalida()`

---

## Cómo demostrarlo en la sustentación

1. **Mostrar una regla que se cumple:** abrir `ArquitecturaTest.java` y ejecutar:
   ```powershell
   cd backend
   .\mvnw.cmd test "-Dtest=ArquitecturaTest"
   ```
   Resultado: `Tests run: 7, Failures: 0`.
2. **Romper una regla a propósito y ver que la prueba lo detecta:**
   1. En `backend/src/main/java/com/udec/restoflow/domain/model/Pedido.java` agregar arriba
      `import org.springframework.stereotype.Component;`.
   2. Ejecutar de nuevo la prueba. Falla con el mensaje:
      `El dominio solo puede usar Java y el propio dominio … Pedido.java → org.springframework.stereotype.Component`.
   3. Quitar la línea.
3. **Ver SOLID en SonarQube:** el análisis (`docs/sonarqube.md`) mide la complejidad, la duplicación y la
   mantenibilidad (calificación de A a E) de cada clase. Las clases pequeñas, con una sola responsabilidad, tienen
   menos complejidad y menos problemas.
