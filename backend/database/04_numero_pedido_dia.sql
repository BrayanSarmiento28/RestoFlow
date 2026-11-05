/* =====================================================================
   RestoFlow · Número del pedido en el día (vuelve a empezar en 1 cada día) y método de pago
   Ejecutar DESPUÉS de 03_registro_peticiones.sql.
   En desarrollo no es obligatorio (Spring Boot agrega la columna solo con ddl-auto: update);
   en producción sí, porque allí las tablas solo cambian por script.

   El id_pedido sigue siendo el identificador interno (no se reinicia); numero_dia es el número que ve
   el administrador: #1, #2... dentro de cada día. metodo_pago: EFECTIVO o TRANSFERENCIA al registrar la venta.
   Los pedidos anteriores a este cambio quedan en NULL.
   ===================================================================== */

USE RestoFlowDB;
GO

IF COL_LENGTH('pedidos', 'numero_dia') IS NULL
    ALTER TABLE pedidos ADD numero_dia INT NULL;
GO

IF COL_LENGTH('pedidos', 'metodo_pago') IS NULL
    ALTER TABLE pedidos ADD metodo_pago VARCHAR(20) NULL
        CONSTRAINT ck_pedidos_metodo_pago CHECK (metodo_pago IN ('EFECTIVO', 'TRANSFERENCIA'));
GO
