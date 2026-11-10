/* =====================================================================
   RestoFlow · Lecturas sin bloqueo (READ_COMMITTED_SNAPSHOT) · RES-45
   Ejecutar DESPUÉS de 05_adiciones.sql. Sirve igual en desarrollo, pre-producción y producción.

   Hallazgo de la prueba de carga (RES-56): con 30 usuarios a la vez, SQL Server mostraba deadlocks al cobrar
   (error 1205) y todo el sistema se detenía de 5 a 11 segundos. Por defecto, en SQL Server quien LEE espera a
   quien ESCRIBE: mientras un cobro descontaba inventario, las pantallas de Cocina, Pedidos y el Dashboard
   esperaban, y dos cobros que leían las alertas se esperaban entre sí en círculo.

   Con READ_COMMITTED_SNAPSHOT cada lectura ve la última versión CONFIRMADA de los datos (SQL Server guarda la
   versión anterior en tempdb) y ya no espera los bloqueos de escritura. Es la configuración que Microsoft
   recomienda para aplicaciones web y la que usan por defecto Azure SQL Database y PostgreSQL.
   Las escrituras sobre el mismo insumo siguen en orden gracias al bloqueo de la aplicación
   (IngredienteJpaRepository.bloquearPorIds).

   WITH ROLLBACK IMMEDIATE: el cambio necesita que nadie más esté usando la base; solo se ejecuta la primera vez
   (si ya está activo, no hace nada).

   Además, un índice para las alertas pendientes de un insumo: esa consulta se hace después de cada descuento de
   inventario y, sin índice, recorría la tabla completa de alertas.
   ===================================================================== */

USE master;
GO

IF EXISTS (SELECT 1 FROM sys.databases WHERE name = 'RestoFlowDB' AND is_read_committed_snapshot_on = 0)
    ALTER DATABASE RestoFlowDB SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO

SELECT name AS base_de_datos, is_read_committed_snapshot_on AS lecturas_sin_bloqueo
FROM sys.databases WHERE name = 'RestoFlowDB';
GO

USE RestoFlowDB;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_alertas_ingrediente_atendida')
    CREATE INDEX ix_alertas_ingrediente_atendida ON alertas (id_ingrediente, atendida);
GO
