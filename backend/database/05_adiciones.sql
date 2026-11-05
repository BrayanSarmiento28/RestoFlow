/* =====================================================================
   RestoFlow · Adiciones: productos que el cliente pide de más con el pedido ya en cocina
   Ejecutar DESPUÉS de 04_numero_pedido_dia.sql.
   En desarrollo no es obligatorio (Spring Boot agrega las columnas solo con ddl-auto: update);
   en producción sí, porque allí las tablas solo cambian por script.

   detalle_pedidos.adicion: 1 si la línea se agregó después de enviar el pedido a cocina.
   pedidos.fecha_adicion: hora de la última adición (desde ahí se mide la demora en cocina).
   Los datos anteriores a este cambio quedan como "no es adición" y sin fecha.
   ===================================================================== */

USE RestoFlowDB;
GO

IF COL_LENGTH('detalle_pedidos', 'adicion') IS NULL
    ALTER TABLE detalle_pedidos ADD adicion BIT NULL CONSTRAINT df_detalle_pedidos_adicion DEFAULT 0;
GO

IF COL_LENGTH('pedidos', 'fecha_adicion') IS NULL
    ALTER TABLE pedidos ADD fecha_adicion DATETIME2 NULL;
GO
