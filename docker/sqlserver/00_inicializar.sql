/* =====================================================================
   RestoFlow · Docker · Preparación de SQL Server
   Lo ejecuta el servicio "db-init" de docker-compose.yml antes de los scripts
   01 y 02. Crea la base de datos y el usuario de la aplicación con la contraseña
   que llega desde el archivo .env (variable de sqlcmd $(APP_PASSWORD)), para que
   ninguna contraseña quede escrita en el código.
   ===================================================================== */

IF DB_ID('RestoFlowDB') IS NULL
    CREATE DATABASE RestoFlowDB;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'restoflow_app')
    CREATE LOGIN restoflow_app WITH PASSWORD = '$(APP_PASSWORD)', CHECK_POLICY = OFF;
ELSE
    ALTER LOGIN restoflow_app WITH PASSWORD = '$(APP_PASSWORD)';
GO
