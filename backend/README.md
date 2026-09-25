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

## Cómo ejecutarlo

1. **Base de datos:** ejecutar `database/01_crear_base_de_datos.sql` en SQL Server Management Studio
   (requiere autenticación mixta y TCP/IP en el puerto 1433).
2. **Configuración privada:** copiar `src/main/resources/application-local.example.yml` como
   `application-local.yml` y escribir la contraseña de la base de datos y la de Gmail. Este archivo no se sube a GitHub.
3. **Arrancar:** en VS Code, `Ejecutar y depurar` → **Backend RestoFlow** → ▶.
   Al primer arranque se crea el administrador `admin@restoflow.com` / `Admin2026*`.
4. **Probar el API:** abrir `api-pruebas.http` con la extensión REST Client y usar "Send Request".

## Pruebas automáticas

24 pruebas en `src/test/java` (JUnit 5):

| Clase | Qué comprueba |
|---|---|
| `UsuarioTest` | Reglas del usuario: correo, nombre, política de contraseñas, cuenta inactiva |
| `CodigoRecuperacionTest` | Código de 6 dígitos, vencimiento a 15 min, uso único, bloqueo por intentos |
| `IniciarSesionServiceTest` | HU-01 completa, incluido el mensaje genérico ante errores |
| `RecuperarContrasenaServiceTest` | HU-02 completa con reloj simulado |
| `GestionarCuentasServiceTest` | HU-03: cifrado de contraseña y correos duplicados |
| `RestoFlowApplicationTests` | La aplicación completa arranca (con H2 en memoria) |

Se ejecutan desde el panel **Pruebas** (ícono de matraz) de VS Code o con `mvn test`.
Los casos de uso se prueban con **dobles en memoria** (`Dobles.java`) en lugar de SQL Server, BCrypt y Gmail:
eso es posible gracias a la arquitectura hexagonal.
