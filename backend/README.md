# RestoFlow · Backend

Responsable: **Brayan Estiven Sarmiento Vargas**

API REST en **Java 17 · Spring Boot 3.5 · arquitectura hexagonal · SQL Server**.
La explicación de la arquitectura está en [`ARQUITECTURA.md`](ARQUITECTURA.md).

## Funcionalidades (Sprint 1 · RF-01)

| Historia | Endpoint | Acceso |
|---|---|---|
| HU-01 Iniciar sesión | `POST /api/auth/login` | Público |
| HU-02 Recuperar contraseña | `POST /api/auth/recuperar` · `POST /api/auth/restablecer` | Público |
| HU-03 Crear cuenta | `POST /api/usuarios` · `GET /api/usuarios` | Solo ADMIN |
| Usuario de la sesión | `GET /api/auth/me` | Con token |

## Funcionalidades (Sprint 2 · RF-02, RF-03, RF-04)

| Historia | Endpoint | Acceso |
|---|---|---|
| Menú: categorías y productos | `GET /api/categorias` · `GET /api/productos` | Con sesión |
| Crear y editar el menú | `POST /api/categorias` · `POST /api/productos` · `PUT /api/productos/{id}` · `PATCH /api/productos/{id}/disponibilidad` | Solo ADMIN |
| Registrar y enviar pedido a cocina | `POST /api/pedidos` | MESERO, ADMIN |
| Consultar pedidos (filtros mesa y estado) | `GET /api/pedidos` · `GET /api/pedidos/{id}` | MESERO, COCINA, ADMIN |
| Modificar / cancelar pedido (solo Pendiente) | `PUT /api/pedidos/{id}` · `PATCH /api/pedidos/{id}/cancelar` | MESERO, ADMIN |
| Registrar la venta (solo Entregado) | `PATCH /api/pedidos/{id}/pagar` | MESERO, ADMIN |
| Comandas de cocina en orden de llegada | `GET /api/cocina/comandas` | COCINA, ADMIN |
| Cambiar estado de preparación | `PATCH /api/cocina/pedidos/{id}/estado` | COCINA, ADMIN |

Entidades del dominio (`domain/model`) y sus tablas: Usuario, CodigoRecuperacion, Categoria, Producto, Pedido,
DetallePedido, Ingrediente, RecetaIngrediente, MovimientoInventario y Alerta. Las de inventario quedan listas
para el sprint de inventario. Script de las tablas nuevas: `database/02_menu_pedidos_inventario.sql`.

## Cómo ejecutarlo

1. **Base de datos:** ejecutar `database/01_crear_base_de_datos.sql` y luego `database/02_menu_pedidos_inventario.sql` en SQL Server Management Studio
   (requiere autenticación mixta y TCP/IP en el puerto 1433).
2. **Configuración privada:** copiar `src/main/resources/application-local.example.yml` como
   `application-local.yml` y escribir la contraseña de la base de datos y la de Gmail. Este archivo no se sube a GitHub.
3. **Arrancar:** en VS Code, `Ejecutar y depurar` → **Backend RestoFlow** → ▶.
   Al primer arranque se crea el administrador `admin@restoflow.com` / `Admin2026*`.
4. **Probar el API:**
   - **Swagger:** abrir **http://localhost:8080/swagger-ui.html**. Ejecutar `POST /api/auth/login`, copiar el
     `token`, pulsar **Authorize** y pegarlo para probar las rutas protegidas.
   - **REST Client:** abrir `api-pruebas.http` y usar "Send Request".

## Entornos (perfiles de Spring Boot)

| Perfil | Entorno | Cómo se activa | Swagger |
|---|---|---|---|
| `dev` | Desarrollo (por defecto) | Arrancar normal | Sí |
| `preprod` | Pre-producción | `--spring.profiles.active=preprod` o la configuración **Backend RestoFlow (pre-producción)** de VS Code | Sí |
| `prod` | Producción | `SPRING_PROFILES_ACTIVE=prod` + variables `JWT_SECRET`, `ADMIN_PASSWORD`, `CORS_ORIGINS`, `DB_*`, `MAIL_*` | No |

Con Maven: `mvn spring-boot:run -Dspring-boot.run.profiles=preprod`. Detalle en [`../docs/entornos.md`](../docs/entornos.md).

## Pruebas automáticas

52 pruebas en `src/test/java` (JUnit 5):

| Clase | Qué comprueba |
|---|---|
| `UsuarioTest` | Reglas del usuario: correo, nombre, política de contraseñas, cuenta inactiva |
| `CodigoRecuperacionTest` | Código de 6 dígitos, vencimiento a 15 min, uso único, bloqueo por intentos |
| `PedidoTest` | Total del pedido, precio congelado, secuencia de estados sin saltos, modificar y cancelar solo en Pendiente, venta solo de entregados |
| `IngredienteTest` | Stock nunca negativo, alerta de stock mínimo, consumo según receta |
| `IniciarSesionServiceTest` | HU-01 completa, incluido el mensaje genérico ante errores |
| `RecuperarContrasenaServiceTest` | HU-02 completa con reloj simulado |
| `GestionarCuentasServiceTest` | HU-03: cifrado de contraseña y correos duplicados |
| `GestionarPedidosServiceTest` | Pedidos y cocina con dobles en memoria: registrar, modificar, cancelar, flujo completo, orden de llegada |
| `PedidoFlujoIntegracionTest` | El API completo (JWT, roles, JPA con H2): mesero registra, cocina prepara y entrega, mesero cobra; 401, 403 y 409 |
| `RestoFlowApplicationTests` | La aplicación completa arranca (con H2 en memoria) |

Se ejecutan desde el panel **Pruebas** (ícono de matraz) de VS Code o con `mvn test`.
Los casos de uso se prueban con **dobles en memoria** (`Dobles.java` y `DoblesPedidos.java`) en lugar de SQL Server, BCrypt y Gmail:
eso es posible gracias a la arquitectura hexagonal.
