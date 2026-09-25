/* =====================================================================
   RestoFlow · Sprint 1 · Script de base de datos (SQL Server)
   Ejecutar en SQL Server Management Studio con un usuario administrador.
   ===================================================================== */

/* ---------- 1. Base de datos ---------- */
IF DB_ID('RestoFlowDB') IS NULL
    CREATE DATABASE RestoFlowDB;
GO

/* ---------- 2. Usuario de la aplicación (el backend se conecta con él) ----------
   Cambie la contraseña y úsela también en backend/src/main/resources/application-local.yml */
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'restoflow_app')
    CREATE LOGIN restoflow_app WITH PASSWORD = 'RestoFlow2026*', CHECK_POLICY = OFF;
GO

USE RestoFlowDB;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'restoflow_app')
BEGIN
    CREATE USER restoflow_app FOR LOGIN restoflow_app;
    ALTER ROLE db_owner ADD MEMBER restoflow_app;
END
GO

/* ---------- 3. Tablas del Sprint 1 ---------- */
IF OBJECT_ID('usuarios') IS NULL
CREATE TABLE usuarios (
    id_usuario      BIGINT IDENTITY(1,1) PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    email           VARCHAR(120) NOT NULL CONSTRAINT uq_usuarios_email UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    rol             VARCHAR(20)  NOT NULL
                    CONSTRAINT ck_usuarios_rol CHECK (rol IN ('ADMIN', 'MESERO', 'COCINA', 'INVENTARIO')),
    activo          BIT          NOT NULL DEFAULT 1,
    fecha_creacion  DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);
GO

IF OBJECT_ID('codigos_recuperacion') IS NULL
CREATE TABLE codigos_recuperacion (
    id_codigo          BIGINT IDENTITY(1,1) PRIMARY KEY,
    id_usuario         BIGINT       NOT NULL
                       CONSTRAINT fk_codigos_usuario REFERENCES usuarios(id_usuario),
    codigo_hash        VARCHAR(100) NOT NULL,
    fecha_expiracion   DATETIME2    NOT NULL,
    usado              BIT          NOT NULL DEFAULT 0,
    intentos_fallidos  INT          NOT NULL DEFAULT 0
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_codigos_usuario')
    CREATE INDEX ix_codigos_usuario ON codigos_recuperacion (id_usuario, usado);
GO
