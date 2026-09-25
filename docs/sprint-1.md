# Sprint 1 · RF-01 Autenticación y control de acceso

**Objetivo del sprint:** que el personal del restaurante pueda ingresar al sistema de forma segura según su rol,
recuperar su contraseña sin ayuda y que el administrador pueda crear las cuentas del personal.

## Historias de usuario

### HU-01 · Iniciar sesión
**Como** empleado del restaurante **quiero** ingresar con mi correo y contraseña **para** acceder a las funciones de mi rol.

Criterios de aceptación:
- Con credenciales correctas, el sistema entrega un token de sesión y muestra la pantalla de mi rol.
- Con credenciales incorrectas, el sistema muestra "Correo o contraseña incorrectos" sin indicar cuál falló.
- Una cuenta desactivada no puede ingresar.

### HU-02 · Recuperar contraseña
**Como** empleado **quiero** restablecer mi contraseña si la olvido **para** no depender del administrador.

Criterios de aceptación:
- Al escribir mi correo, recibo un código de 6 dígitos que vence en 15 minutos.
- Con el código correcto puedo definir una contraseña nueva (mínimo 8 caracteres).
- El código solo puede usarse una vez.
- Por seguridad, el sistema responde lo mismo aunque el correo no exista.

### HU-03 · Crear cuenta de empleado
**Como** administrador **quiero** crear las cuentas del personal y asignarles un rol **para** controlar quién accede al sistema.

Criterios de aceptación:
- Solo un usuario con rol ADMIN puede crear cuentas.
- Datos requeridos: nombre, correo (único), contraseña temporal y rol (ADMIN, MESERO, COCINA, INVENTARIO).
- La contraseña se guarda cifrada (BCrypt), nunca en texto plano.
- Al iniciar el sistema por primera vez se crea automáticamente un administrador inicial.

## Tareas

| # | Tarea | Responsable |
|---|---|---|
| B1 | Proyecto Spring Boot base | Brayan |
| B2 | Estructura de carpetas hexagonal | Brayan |
| B3 | Dominio: Usuario, Rol, CodigoRecuperacion | Brayan |
| B4 | Puertos de entrada y salida | Brayan |
| B5 | Casos de uso: login, crear cuenta, recuperar contraseña | Brayan |
| B6 | Persistencia JPA + SQL Server | Brayan |
| B7 | Seguridad: JWT, BCrypt y roles | Brayan |
| B8 | Envío de correo (Gmail SMTP) | Brayan |
| B9 | Controladores REST y manejo de errores | Brayan |
| B10 | Pruebas unitarias del dominio | Brayan |
| F1 | Proyecto Angular base | Sebastián |
| F2 | Servicios, modelos e interceptor JWT | Sebastián |
| F3 | Pantalla Iniciar sesión | Sebastián |
| F4 | Pantalla Olvidé mi contraseña | Sebastián |
| F5 | Pantalla Crear cuenta (solo admin) | Sebastián |

## Modelo de datos del sprint

| Tabla | Campos |
|---|---|
| `usuarios` | id_usuario, nombre, email (único), password_hash, rol, activo, fecha_creacion |
| `codigos_recuperacion` | id_codigo, id_usuario (FK), codigo_hash, fecha_expiracion, usado |

## Contrato del API (acordado entre frontend y backend)

| Método | Ruta | Acceso | Cuerpo | Respuesta |
|---|---|---|---|---|
| POST | `/api/auth/login` | Público | `{ email, password }` | `{ token, expiraEnSegundos, usuario }` |
| POST | `/api/auth/recuperar` | Público | `{ email }` | `{ mensaje }` |
| POST | `/api/auth/restablecer` | Público | `{ email, codigo, nuevaPassword }` | `{ mensaje }` |
| GET | `/api/auth/me` | Autenticado | — | `usuario` |
| POST | `/api/usuarios` | ADMIN | `{ nombre, email, password, rol }` | `usuario` (201) |
| GET | `/api/usuarios` | ADMIN | — | `usuario[]` |

`usuario = { id, nombre, email, rol, activo }` · Errores: `{ status, error, mensaje, campos? }`
