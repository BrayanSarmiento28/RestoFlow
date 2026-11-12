/* =====================================================================
   RestoFlow · Registro de actividad en la base de datos (recomendación del comité)
   Ejecutar DESPUÉS de 02_menu_pedidos_inventario.sql.
   En desarrollo no es obligatorio (Spring Boot crea la tabla solo con ddl-auto: update);
   en producción sí, porque allí las tablas solo se crean por script.

   Cada petición al API queda aquí: quién, cuándo, qué ruta, cómo terminó y cuánto tardó.
   Nunca se guarda el cuerpo de la petición (contraseñas, códigos) ni el token.
   La aplicación borra cada madrugada lo que tenga más de 30 días (REGISTRO_BD_DIAS).
   ===================================================================== */

USE RestoFlowDB;
GO

IF OBJECT_ID('registro_peticiones') IS NULL
CREATE TABLE registro_peticiones (
    id_registro   BIGINT IDENTITY(1,1) PRIMARY KEY,
    fecha         DATETIME2     NOT NULL,
    request_id    VARCHAR(64)   NULL,          -- el mismo id de los logs de texto
    usuario       VARCHAR(150)  NULL,          -- correo; NULL si fue anónimo
    rol           VARCHAR(20)   NULL,
    metodo        VARCHAR(10)   NOT NULL,
    ruta          VARCHAR(500)  NOT NULL,
    estado        INT           NOT NULL CONSTRAINT ck_registro_estado CHECK (estado BETWEEN 100 AND 599),
    duracion_ms   BIGINT        NOT NULL CONSTRAINT ck_registro_duracion CHECK (duracion_ms >= 0)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_registro_fecha')
    CREATE INDEX ix_registro_fecha ON registro_peticiones (fecha);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_registro_usuario_fecha')
    CREATE INDEX ix_registro_usuario_fecha ON registro_peticiones (usuario, fecha);
GO
