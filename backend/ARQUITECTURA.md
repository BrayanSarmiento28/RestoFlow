# Arquitectura del backend: hexagonal (puertos y adaptadores)

## La idea en una frase

**El negocio del restaurante está en el centro y no depende de la tecnología.**
La tecnología (Spring, SQL Server, JWT, Gmail) se conecta desde afuera por medio de *adaptadores*
que implementan *puertos* (interfaces).

```
             ADAPTADORES DE ENTRADA                          ADAPTADORES DE SALIDA
             (quién usa el sistema)                          (qué usa el sistema)

  Angular ──► AuthController ─┐                        ┌─► UsuarioPersistenceAdapter ──► SQL Server
                              │                        │
                              ▼                        │
                  ┌──────── APLICACIÓN ───────────┐    ├─► GmailCorreoAdapter ─────────► Gmail
                  │  Puertos de entrada (in)       │    │
                  │  IniciarSesionUseCase ...      │    └─► JwtTokenAdapter
                  │          │                     │
                  │   ┌──── DOMINIO ────┐          │
                  │   │ Usuario, Rol,   │          │
                  │   │ reglas          │          │
                  │   └─────────────────┘          │
                  │  Puertos de salida (out) ──────┼──► (interfaces que implementan los adaptadores)
                  └────────────────────────────────┘
```

**Regla de oro:** las dependencias apuntan siempre hacia adentro.
`infrastructure` conoce a `application`, `application` conoce a `domain`, y `domain` no conoce a nadie.

## Paquetes

| Paquete | Capa | Qué contiene | ¿Usa Spring? |
|---|---|---|---|
| `domain.model` | Dominio | Usuario, Rol, CodigoRecuperacion y sus reglas | No |
| `domain.exception` | Dominio | Errores del negocio | No |
| `application.port.in` | Aplicación | Casos de uso que ofrece el sistema (interfaces) | No |
| `application.port.out` | Aplicación | Lo que el sistema necesita del exterior (interfaces) | No |
| `application.service` | Aplicación | Implementación de los casos de uso | Solo `@Transactional` |
| `infrastructure.adapter.in.web` | Infraestructura | Controladores REST, DTO, manejo de errores | Sí |
| `infrastructure.adapter.out.persistence` | Infraestructura | Entidades JPA y repositorios (SQL Server) | Sí |
| `infrastructure.adapter.out.mail` | Infraestructura | Envío de correo por Gmail | Sí |
| `infrastructure.security` | Infraestructura | JWT, BCrypt, reglas por rol | Sí |
| `infrastructure.config` | Infraestructura | Conexión de puertos con adaptadores, datos iniciales | Sí |

## ¿Por qué esta arquitectura?

1. **Cambiar tecnología sin tocar el negocio.** Si se cambia SQL Server por MySQL o Gmail por otro servicio,
   solo se escribe un adaptador nuevo.
2. **Pruebas fáciles.** Las reglas del dominio se prueban con JUnit en milisegundos, sin base de datos ni servidor.
3. **Orden en el equipo.** Cada clase tiene un lugar claro y una sola responsabilidad.

## Ejemplo del Sprint 1: iniciar sesión

1. Angular envía `POST /api/auth/login` con correo y contraseña → llega a `AuthController` (**adaptador de entrada**).
2. El controlador llama al puerto `IniciarSesionUseCase`.
3. El servicio busca el usuario con el puerto `UsuarioRepositoryPort`, verifica la contraseña con
   `CifradoContrasenaPort` y la regla del dominio *"una cuenta inactiva no puede ingresar"*.
4. Si todo está bien, pide un token al puerto `GeneradorTokenPort`.
5. Los **adaptadores de salida** hacen el trabajo técnico: JPA consulta SQL Server, BCrypt compara la
   contraseña y JWT firma el token.
