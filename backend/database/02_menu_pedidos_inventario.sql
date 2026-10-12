/* =====================================================================
   RestoFlow · Sprint 2 · Tablas del menú, pedidos e inventario (SQL Server)
   Ejecutar DESPUÉS de 01_crear_base_de_datos.sql.
   En desarrollo no es obligatorio (Spring Boot crea las tablas solo con ddl-auto: update);
   en producción sí, porque allí las tablas solo se crean por script.
   ===================================================================== */

USE RestoFlowDB;
GO

/* ---------- Menú ---------- */
IF OBJECT_ID('categorias') IS NULL
CREATE TABLE categorias (
    id_categoria  BIGINT IDENTITY(1,1) PRIMARY KEY,
    nombre        VARCHAR(60) NOT NULL CONSTRAINT uq_categorias_nombre UNIQUE,
    activa        BIT         NOT NULL DEFAULT 1
);
GO

IF OBJECT_ID('productos') IS NULL
CREATE TABLE productos (
    id_producto   BIGINT IDENTITY(1,1) PRIMARY KEY,
    nombre        VARCHAR(100)   NOT NULL,
    descripcion   VARCHAR(300)   NULL,
    precio        DECIMAL(12,2)  NOT NULL CONSTRAINT ck_productos_precio CHECK (precio > 0),
    id_categoria  BIGINT         NOT NULL CONSTRAINT fk_productos_categoria REFERENCES categorias(id_categoria),
    activo        BIT            NOT NULL DEFAULT 1
);
GO

/* ---------- Pedidos (RF-02, RF-03, RF-04) ---------- */
IF OBJECT_ID('pedidos') IS NULL
CREATE TABLE pedidos (
    id_pedido                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    numero_mesa               INT           NOT NULL CONSTRAINT ck_pedidos_mesa CHECK (numero_mesa > 0),
    id_usuario                BIGINT        NOT NULL CONSTRAINT fk_pedidos_usuario REFERENCES usuarios(id_usuario),
    observaciones             VARCHAR(300)  NULL,
    estado_cocina             VARCHAR(20)   NOT NULL
                              CONSTRAINT ck_pedidos_estado_cocina
                              CHECK (estado_cocina IN ('PENDIENTE', 'EN_PREPARACION', 'LISTO', 'ENTREGADO')),
    estado_pedido             VARCHAR(20)   NOT NULL
                              CONSTRAINT ck_pedidos_estado_pedido CHECK (estado_pedido IN ('ABIERTO', 'PAGADO', 'CANCELADO')),
    total                     DECIMAL(12,2) NOT NULL DEFAULT 0,
    fecha_creacion            DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    fecha_inicio_preparacion  DATETIME2     NULL,
    fecha_listo               DATETIME2     NULL,
    fecha_entrega             DATETIME2     NULL,
    fecha_cierre              DATETIME2     NULL
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_pedidos_estados')
    CREATE INDEX ix_pedidos_estados ON pedidos (estado_pedido, estado_cocina);
GO

IF OBJECT_ID('detalle_pedidos') IS NULL
CREATE TABLE detalle_pedidos (
    id_detalle       BIGINT IDENTITY(1,1) PRIMARY KEY,
    id_pedido        BIGINT        NOT NULL CONSTRAINT fk_detalle_pedido REFERENCES pedidos(id_pedido),
    id_producto      BIGINT        NOT NULL CONSTRAINT fk_detalle_producto REFERENCES productos(id_producto),
    cantidad         INT           NOT NULL CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
    precio_unitario  DECIMAL(12,2) NOT NULL,
    subtotal         DECIMAL(12,2) NOT NULL,
    nota             VARCHAR(200)  NULL
);
GO

/* ---------- Inventario (se usa a partir del sprint de inventario: RF-05, RF-06, RF-07, RF-12) ---------- */
IF OBJECT_ID('ingredientes') IS NULL
CREATE TABLE ingredientes (
    id_ingrediente  BIGINT IDENTITY(1,1) PRIMARY KEY,
    nombre          VARCHAR(100)  NOT NULL CONSTRAINT uq_ingredientes_nombre UNIQUE,
    unidad_medida   VARCHAR(20)   NOT NULL
                    CONSTRAINT ck_ingredientes_unidad CHECK (unidad_medida IN ('GRAMO', 'KILOGRAMO', 'MILILITRO', 'LITRO', 'UNIDAD')),
    stock_actual    DECIMAL(12,3) NOT NULL CONSTRAINT ck_ingredientes_stock CHECK (stock_actual >= 0),
    stock_minimo    DECIMAL(12,3) NOT NULL CONSTRAINT ck_ingredientes_minimo CHECK (stock_minimo >= 0),
    activo          BIT           NOT NULL DEFAULT 1
);
GO

IF OBJECT_ID('receta_ingredientes') IS NULL
CREATE TABLE receta_ingredientes (
    id_receta       BIGINT IDENTITY(1,1) PRIMARY KEY,
    id_producto     BIGINT        NOT NULL CONSTRAINT fk_receta_producto REFERENCES productos(id_producto),
    id_ingrediente  BIGINT        NOT NULL CONSTRAINT fk_receta_ingrediente REFERENCES ingredientes(id_ingrediente),
    cantidad        DECIMAL(12,3) NOT NULL CONSTRAINT ck_receta_cantidad CHECK (cantidad > 0),
    CONSTRAINT uq_receta_producto_ingrediente UNIQUE (id_producto, id_ingrediente)
);
GO

IF OBJECT_ID('movimientos_inventario') IS NULL
CREATE TABLE movimientos_inventario (
    id_movimiento   BIGINT IDENTITY(1,1) PRIMARY KEY,
    id_ingrediente  BIGINT        NOT NULL CONSTRAINT fk_movimiento_ingrediente REFERENCES ingredientes(id_ingrediente),
    tipo            VARCHAR(20)   NOT NULL CONSTRAINT ck_movimiento_tipo CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE')),
    cantidad        DECIMAL(12,3) NOT NULL,
    motivo          VARCHAR(200)  NULL,
    id_usuario      BIGINT        NULL CONSTRAINT fk_movimiento_usuario REFERENCES usuarios(id_usuario),
    id_pedido       BIGINT        NULL CONSTRAINT fk_movimiento_pedido REFERENCES pedidos(id_pedido),
    fecha           DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_movimientos_ingrediente_fecha')
    CREATE INDEX ix_movimientos_ingrediente_fecha ON movimientos_inventario (id_ingrediente, fecha);
GO

IF OBJECT_ID('alertas') IS NULL
CREATE TABLE alertas (
    id_alerta       BIGINT IDENTITY(1,1) PRIMARY KEY,
    id_ingrediente  BIGINT        NULL CONSTRAINT fk_alerta_ingrediente REFERENCES ingredientes(id_ingrediente),
    mensaje         VARCHAR(300)  NOT NULL,
    prioridad       VARCHAR(10)   NOT NULL CONSTRAINT ck_alerta_prioridad CHECK (prioridad IN ('ALTA', 'MEDIA', 'BAJA')),
    atendida        BIT           NOT NULL DEFAULT 0,
    fecha_creacion  DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    fecha_atencion  DATETIME2     NULL
);
GO
